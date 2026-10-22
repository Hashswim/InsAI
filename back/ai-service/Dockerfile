FROM python:3.11-slim
WORKDIR /service
ENV APP_ENV=production JEV_PROVIDER=gemini
COPY pyproject.toml ./
COPY README.md ./
COPY app ./app
RUN pip install --no-cache-dir .
RUN useradd --create-home appuser
USER appuser
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8085/health/live', timeout=2)"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8085", "--no-access-log"]
