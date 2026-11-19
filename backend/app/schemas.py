from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

# Formato E.164: "+" y entre 8 y 15 dígitos
PHONE_PATTERN = r"^\+[1-9]\d{7,14}$"


# ---------- Autenticación ----------
class PhoneIn(BaseModel):
    phone: str = Field(pattern=PHONE_PATTERN, examples=["+573001234567"])


class VerifyIn(PhoneIn):
    code: str = Field(pattern=r"^\d{6}$", examples=["123456"])


class CodeOut(BaseModel):
    message: str
    dev_code: str | None = None   # solo se llena si DEV_MODE=true


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"


# ---------- Consentimientos ----------
class ConsentOut(BaseModel):
    id: int
    observer_id: int
    observer_phone: str
    target_id: int
    target_phone: str
    status: str
    created_at: datetime


class TargetOut(BaseModel):
    """Persona que me autorizó a ver su ubicación."""
    user_id: int
    phone: str


# ---------- Ubicaciones ----------
class LocationIn(BaseModel):
    # Los rangos validan que sean coordenadas reales (rechaza basura o ataques)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    accuracy: float | None = Field(default=None, ge=0, le=100_000)
    recorded_at: datetime | None = None   # si no viene, se usa la hora del servidor


class LocationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    latitude: float
    longitude: float
    accuracy: float | None
    recorded_at: datetime