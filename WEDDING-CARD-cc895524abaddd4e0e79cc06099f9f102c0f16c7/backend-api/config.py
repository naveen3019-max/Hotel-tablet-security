from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field

class Settings(BaseSettings):
    # MongoDB
    mongodb_url: str = Field(
        default="mongodb://localhost:27017",
        env="MONGODB_URL"
    )
    database_name: str = Field(
        default="hotel_security",
        env="DATABASE_NAME"
    )

    # Auth
    secret_key: str = Field(
        default="dev-secret-key-change-in-prod",
        env="SECRET_KEY"
    )
    api_token: str = Field(
        default="dev-api-token",
        env="API_TOKEN"
    )

    jwt_algorithm: str = Field(default="HS256", env="JWT_ALGORITHM")
    jwt_expiration_minutes: int = Field(default=43200, env="JWT_EXPIRATION_MINUTES")

    # SMTP
    smtp_enabled: bool = Field(default=False, env="SMTP_ENABLED")
    smtp_host: str = Field(default="smtp.gmail.com", env="SMTP_HOST")
    smtp_port: int = Field(default=587, env="SMTP_PORT")
    smtp_username: str = Field(default="", env="SMTP_USERNAME")
    smtp_password: str = Field(default="", env="SMTP_PASSWORD")
    smtp_from_email: str = Field(default="alerts@hotel-security.com", env="SMTP_FROM_EMAIL")
    alert_email_recipients: str = Field(default="", env="ALERT_EMAIL_RECIPIENTS")

    # Slack
    slack_enabled: bool = Field(default=False, env="SLACK_ENABLED")
    slack_webhook_url: str = Field(default="", env="SLACK_WEBHOOK_URL")

    # Redis
    redis_url: str = Field(default="redis://localhost:6379/0", env="REDIS_URL")
    celery_enabled: bool = Field(default=True, env="CELERY_ENABLED")

    # App
    app_env: str = Field(default="development", env="APP_ENV")
    debug: bool = Field(default=True, env="DEBUG")
    cors_origins: str = Field(default="*", env="CORS_ORIGINS")

    model_config = SettingsConfigDict(
        env_file=".env",
        case_sensitive=True,
        extra="ignore"  # REQUIRED for Windows + reload
    )

settings = Settings()
print("Mongo URL =", settings.mongodb_url)
