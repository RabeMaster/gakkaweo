FROM python:3.13-slim

WORKDIR /app

COPY requirements.lock constraints-docker.txt ./
RUN pip install --no-cache-dir -r requirements.lock \
    -c constraints-docker.txt \
    --extra-index-url https://download.pytorch.org/whl/cpu

COPY app/ ./app/

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
