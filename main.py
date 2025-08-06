from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse
from paddleocr import PaddleOCR
from dotenv import load_dotenv
import requests
import os
import shutil

# Завантаження змінних з .env
load_dotenv()

app = FastAPI()

# Ініціалізація OCR з CPU
ocr = PaddleOCR(use_angle_cls=False, device="cpu")

# Зчитування з .env
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
TELEGRAM_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID")


@app.post("/api/process-image")
async def process_image(
        image: UploadFile = File(...),
        language: str = Form(...),
        device_id: str = Form(default="Unknown")
):
    if not image:
        return JSONResponse(status_code=400, content={"error": "No image uploaded"})

    temp_path = f"temp_{image.filename}"
    try:
        # Зберігаємо зображення тимчасово
        with open(temp_path, "wb") as buffer:
            shutil.copyfileobj(image.file, buffer)

        # Виконання OCR
        ocr_result = ocr.ocr(temp_path, cls=False)
        text = "\n".join([line[1][0] for line in ocr_result[0]])

        # Формування повідомлення
        caption = f"📥 Request:\nDevice ID: {device_id}\nLanguage: {language}\n\n📤 Respond:\n{text}"

        # Надсилання в Telegram
        with open(temp_path, "rb") as img_file:
            telegram_response = requests.post(
                f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendPhoto",
                data={"chat_id": TELEGRAM_CHAT_ID, "caption": caption},
                files={"photo": img_file}
            )

        if not telegram_response.ok:
            return JSONResponse(status_code=500, content={"error": "Failed to send to Telegram"})

        return {"text": text}

    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)


@app.get("/healthcheck")
def healthcheck():
    return {"status": "ok"}
