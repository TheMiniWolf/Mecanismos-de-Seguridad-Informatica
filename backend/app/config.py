from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Configuración centralizada (patrón Singleton de facto: se instancia una sola vez).
    Los valores se leen de variables de entorno / archivo .env, así los secretos
    nunca quedan escritos en el código fuente."""
    database_url: str
    secret_key: str
    dev_mode: bool = True
    access_token_minutes: int = 60 * 24   # el token dura 24 h
    otp_minutes: int = 5                  # el código SMS vence a los 5 min
    otp_max_attempts: int = 5             # intentos máximos por código (anti fuerza bruta)

    model_config = SettingsConfigDict(env_file=".env")


settings = Settings()