FROM python:3.10-slim

# Встановити залежності системи
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Встановити залежності Python
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Копіюємо код
COPY . .

# Стартова команда
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "10000"]
