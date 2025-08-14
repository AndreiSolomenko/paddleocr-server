package com.onrender.highlightserverspring;

import net.sourceforge.tess4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class OCRController {

    @Value("${telegram.bot.token}")
    private String TELEGRAM_BOT_TOKEN;

    @Value("${telegram.chat.id}")
    private String TELEGRAM_CHAT_ID;

    @PostMapping("/api/process-image")
    public Map<String, String> processImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("language") String language,
            @RequestParam(name = "device_id", required = false) String deviceId
    ) {
        try {
            Map<String, String> response = new HashMap<>();

            if (image == null || image.isEmpty()) {
                response.put("Error:", "No image loaded.");
                return response;
            }
            if (language == null || language.trim().isEmpty()) {
                response.put("Error:", "The language is not specified.");
                return response;
            }

            String recognizedText;

            if ("eng".equalsIgnoreCase(language)) {
                recognizedText = processWithPaddle(image);
            } else {
                BufferedImage bufferedImage = ImageIO.read(image.getInputStream());
                ITesseract instance = new Tesseract();
                instance.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata");
                instance.setLanguage(language);
                recognizedText = instance.doOCR(bufferedImage);
            }

            response.put("text", recognizedText);

            sendTelegramImageAndText(image, language, recognizedText, deviceId);

            return response;

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error occurred while processing the image", e);
        } catch (TesseractException e) {
            throw new RuntimeException("OCR error", e);
        }
    }






    private String processWithPaddle(MultipartFile image) throws IOException, InterruptedException {
        Path filePath = null;
        try {
            filePath = saveImageToLocalAndGetPath(image);

//            String imageUrl = "https://highlight.cresotech.com/tempuploads/" + filePath.getFileName();
            String imageUrl = "https://paddleocr-server.onrender.com/tempuploads/" + filePath.getFileName();

            String jsonPayload = """
        {
          "file": "%s",
          "useDocOrientationClassify": true,
          "useDocUnwarping": false,
          "useTextlineOrientation": true
        }
        """.formatted(imageUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://eng.paddle.digsee.com/ocr"))
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> httpResponse = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            String responseBody = httpResponse.body();

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<?, ?> map = mapper.readValue(responseBody, Map.class);

            Map<?, ?> result = (Map<?, ?>) map.get("result");
            if (result == null) {
                throw new RuntimeException("No result in Paddle OCR response");
            }

            java.util.List<?> ocrResults = (java.util.List<?>) result.get("ocrResults");
            if (ocrResults == null || ocrResults.isEmpty()) {
                throw new RuntimeException("No ocrResults found");
            }

            Map<?, ?> firstOcrResult = (Map<?, ?>) ocrResults.get(0);

            Map<?, ?> prunedResult = (Map<?, ?>) firstOcrResult.get("prunedResult");
            if (prunedResult == null) {
                throw new RuntimeException("No prunedResult found");
            }

            java.util.List<String> recTexts = (java.util.List<String>) prunedResult.get("rec_texts");
            if (recTexts == null) {
                throw new RuntimeException("No rec_texts found");
            }

            return String.join(" ", recTexts);

        } finally {
            if (filePath != null) {
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    System.err.println("Failed to delete temp file: " + filePath);
                }
            }
        }
    }

    private Path saveImageToLocalAndGetPath(MultipartFile image) throws IOException {
        Path uploadDir = Paths.get("tempuploads");

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        String filename = UUID.randomUUID() + "-" + image.getOriginalFilename();
        Path filePath = uploadDir.resolve(filename);

        Files.copy(image.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return filePath;
    }










    private void sendTelegramImageAndText(MultipartFile image, String language, String recognizedText, String deviceId) {
        try {
            String urlPhoto = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/sendPhoto";

            HttpRequest.BodyPublisher body = buildMultipartBody(image, TELEGRAM_CHAT_ID, language, recognizedText, deviceId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlPhoto))
                    .header("Content-Type", "multipart/form-data; boundary=---011000010111000001101001")
                    .POST(body)
                    .build();

            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private HttpRequest.BodyPublisher buildMultipartBody(MultipartFile image, String chatId, String language, String recognizedText, String deviceId) throws IOException {
        String boundary = "---011000010111000001101001";
        String CRLF = "\r\n";

        StringBuilder metadataPart = new StringBuilder();
        metadataPart.append("--").append(boundary).append(CRLF);
        metadataPart.append("Content-Disposition: form-data; name=\"chat_id\"").append(CRLF).append(CRLF);
        metadataPart.append(chatId).append(CRLF);

        metadataPart.append("--").append(boundary).append(CRLF);
        metadataPart.append("Content-Disposition: form-data; name=\"caption\"").append(CRLF).append(CRLF);
        metadataPart.append("📥 Request:\n")
                .append("Device ID: ").append(deviceId != null ? deviceId : "No deviceId").append("\n")
                .append("Language: ").append(language).append("\n\n")
                .append("📤 Respond:\n").append(recognizedText).append(CRLF);

        metadataPart.append("--").append(boundary).append(CRLF);
        metadataPart.append("Content-Disposition: form-data; name=\"photo\"; filename=\"image.jpg\"").append(CRLF);
        metadataPart.append("Content-Type: image/jpeg").append(CRLF).append(CRLF);

        byte[] imageBytes = image.getBytes();
        byte[] metaBytes = metadataPart.toString().getBytes();
        byte[] endBytes = (CRLF + "--" + boundary + "--" + CRLF).getBytes();

        return HttpRequest.BodyPublishers.ofByteArrays(
                java.util.List.of(metaBytes, imageBytes, endBytes)
        );
    }


    @GetMapping("/healthcheck")
    public String healthcheck() {
        return "is working";
    }


    @GetMapping("/")
    public String root() {
        return "ok";
    }


    @GetMapping("/captcha")
    public String captchaPage() {
        return "captcha";
    }


}
