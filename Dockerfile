FROM python:3.10-slim

# Встановлюємо системні залежності
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    gcc \
    g++ \
    make \
    && rm -rf /var/lib/apt/lists/*

# Встановлюємо Python-залежності
COPY requirements.txt .
RUN pip install --upgrade pip setuptools wheel
RUN pip install --no-cache-dir -r requirements.txt

# Копіюємо код
COPY . .

# Стартова команда
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "10000"]
