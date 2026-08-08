# ai-service/Dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
EXPOSE 8000
# --http h11 pins the HTTP protocol implementation uvicorn uses. The default
# ("auto") picks httptools, whose HTTP/2 cleartext (h2c) upgrade handling can
# trip up the backend's JDK RestClient when it issues container-to-container
# requests; h11 avoids that class of issue and is well-supported for this
# service's request volume.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--http", "h11"]
