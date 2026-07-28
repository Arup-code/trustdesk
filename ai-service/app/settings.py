from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", extra="ignore")

    data_dir: str = "../data"
    ai_model_mode: str = "mock"
    openrouter_api_key: str = ""
    openrouter_model: str = "openrouter/auto"
    java_base_url: str = "http://localhost:8080"
    internal_api_key: str = "dev-internal-key-change-me"
    eval_java_username: str = "agent1"
    eval_java_password: str = "agent123"


settings = Settings()
