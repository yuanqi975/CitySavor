from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    database_url: str = "sqlite:///./todo.db"
    secret_key: str = "replace-with-a-long-random-secret-in-production-2026"
    access_token_minutes: int = 60


settings = Settings()
