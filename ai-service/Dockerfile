# python:3.14-slim (CPU-only, PyTorch CPU wheel)
FROM python:3.14-slim

WORKDIR /app

COPY requirements.lock constraints-docker.txt ./
RUN pip install --no-cache-dir -r requirements.lock \
    -c constraints-docker.txt \
    --extra-index-url https://download.pytorch.org/whl/cpu

COPY app/ ./app/

RUN groupadd --gid 1000 appgroup \
    && useradd --uid 1000 --gid appgroup --no-create-home appuser
USER appuser

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
