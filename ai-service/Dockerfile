FROM python:3.13-slim

WORKDIR /app

COPY requirements.lock ./
RUN pip install --no-cache-dir torch==2.10.0 --index-url https://download.pytorch.org/whl/cpu && \
    pip install --no-cache-dir -r requirements.lock

COPY app/ ./app/

EXPOSE 8000 

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
