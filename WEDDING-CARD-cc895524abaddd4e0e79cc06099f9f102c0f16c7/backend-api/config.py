import os
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field

class Settings(BaseSettings):
    # MongoDB
    mongodb_url: str = Field(
        default_factory=lambda: (
            os.environ.get("MONGODB_URL") or
            os.getenv("MONGODB_URL") or
            "mongodb://localhost:27017"
        )
    )
    database_name: str = Field(
        default_factory=lambda: (
            os.environ.get("DATABASE_NAME") or
            "hotel_security"
        )
    )

    # Auth
    secret_key: str = Field(
        default_factory=lambda: (
            os.environ.get("SECRET_KEY") or
            "dev-secret-key-change-in-prod"
        )
    )
    api_token: str = Field(
        default_factory=lambda: (
            os.environ.get("API_TOKEN") or
            "dev-api-token"
        )
    )

    jwt_algorithm: str = Field(
        default_factory=lambda: (
            os.environ.get("JWT_ALGORITHM") or
            "HS256"
        )
    )
    jwt_expiration_minutes: int = Field(
        default_factory=lambda: int(
            os.environ.get("JWT_EXPIRATION_MINUTES") or
            43200
        )
    )

    # SMTP
    smtp_enabled: bool = Field(
        default_factory=lambda: str(
            os.environ.get("SMTP_ENABLED") or
            "False"
        ).lower() in ("true", "1", "yes")
    )
    smtp_host: str = Field(
        default_factory=lambda: (
            os.environ.get("SMTP_HOST") or
            "smtp.gmail.com"
        )
    )
    smtp_port: int = Field(
        default_factory=lambda: int(
            os.environ.get("SMTP_PORT") or
            587
        )
    )
    smtp_username: str = Field(
        default_factory=lambda: (
            os.environ.get("SMTP_USERNAME") or
            ""
        )
    )
    smtp_password: str = Field(
        default_factory=lambda: (
            os.environ.get("SMTP_PASSWORD") or
            ""
        )
    )
    smtp_from_email: str = Field(
        default_factory=lambda: (
            os.environ.get("SMTP_FROM_EMAIL") or
            "alerts@hotel-security.com"
        )
    )
    alert_email_recipients: str = Field(
        default_factory=lambda: (
            os.environ.get("ALERT_EMAIL_RECIPIENTS") or
            ""
        )
    )

    # Slack
    slack_enabled: bool = Field(
        default_factory=lambda: str(
            os.environ.get("SLACK_ENABLED") or
            "False"
        ).lower() in ("true", "1", "yes")
    )
    slack_webhook_url: str = Field(
        default_factory=lambda: (
            os.environ.get("SLACK_WEBHOOK_URL") or
            ""
        )
    )

    # Redis
    redis_url: str = Field(
        default_factory=lambda: (
            os.environ.get("REDIS_URL") or
            "redis://localhost:6379/0"
        )
    )
    celery_enabled: bool = Field(
        default_factory=lambda: str(
            os.environ.get("CELERY_ENABLED") or
            "True"
        ).lower() in ("true", "1", "yes")
    )

    # App
    app_env: str = Field(
        default_factory=lambda: (
            os.environ.get("APP_ENV") or
            "development"
        )
    )
    debug: bool = Field(
        default_factory=lambda: str(
            os.environ.get("DEBUG") or
            "True"
        ).lower() in ("true", "1", "yes")
    )
    cors_origins: str = Field(
        default_factory=lambda: (
            os.environ.get("CORS_ORIGINS") or
            "*"
        )
    )

    model_config = SettingsConfigDict(
        env_file=".env",
        case_sensitive=True,
        extra="ignore"  # REQUIRED for Windows + reload
    )

settings = Settings()

if "localhost" in settings.mongodb_url:
    import sys
    print("⚠️ WARNING: Using localhost MongoDB!", flush=True)
    print("Set MONGODB_URL environment variable!", flush=True)
else:
    print(f"✅ MongoDB Atlas connected: {settings.mongodb_url[:50]}...", flush=True)

