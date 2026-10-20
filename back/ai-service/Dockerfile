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
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8085", "--no-access-log"]
