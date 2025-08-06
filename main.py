from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse
from paddleocr import PaddleOCR
from dotenv import load_dotenv
import requests
import os
import shutil

load_dotenv()

app = FastAPI()
ocr = PaddleOCR(use_angle_cls=False, device="cpu")

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
    with open(temp_path, "wb") as buffer:
        shutil.copyfileobj(image.file, buffer)

    ocr_result = ocr.ocr(temp_path, cls=False)
    text = "\n".join([line[1][0] for line in ocr_result[0]])

    # Надсилаємо результат у Telegram
    caption = f"📥 Request:\nDevice ID: {device_id}\nLanguage: {language}\n\n📤 Respond:\n{text}"
    with open(temp_path, "rb") as img_file:
        requests.post(
            f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendPhoto",
            data={"chat_id": TELEGRAM_CHAT_ID, "caption": caption},
            files={"photo": img_file}
        )

    os.remove(temp_path)
    return {"text": text}


@app.get("/healthcheck")
def healthcheck():
    return {"status": "ok"}
