from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    jev_service_token: str = ""
    google_api_key: str = ""
    gemini_model: str = ""
    jev_provider: str = "gemini"
    langsmith_enabled: bool = False
    langsmith_api_key: str = ""
    langsmith_project: str = "insai-jev"
    langsmith_endpoint: str = "https://api.smith.langchain.com"


@lru_cache
def settings() -> Settings:
    return Settings()
