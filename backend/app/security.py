import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models import User

# Esquema "Authorization: Bearer <token>"; también activa el botón Authorize en /docs
bearer_scheme = HTTPBearer()


def generate_otp() -> str:
    """Código de 6 dígitos con generador criptográfico (secrets, no random)."""
    return f"{secrets.randbelow(1_000_000):06d}"


def hash_otp(phone: str, code: str) -> str:
    """HMAC-SHA256 con la clave secreta: aunque roben la BD no pueden deducir los códigos."""
    msg = f"{phone}:{code}".encode()
    return hmac.new(settings.secret_key.encode(), msg, hashlib.sha256).hexdigest()


def create_access_token(user_id: int) -> str:
    payload = {
        "sub": str(user_id),
        "exp": datetime.now(timezone.utc) + timedelta(minutes=settings.access_token_minutes),
    }
    return jwt.encode(payload, settings.secret_key, algorithm="HS256")


def get_current_user(
    creds: HTTPAuthorizationCredentials = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> User:
    """Dependencia reutilizable: cualquier endpoint que la declare queda protegido.
    Valida firma y expiración del JWT y devuelve el usuario autenticado."""
    try:
        data = jwt.decode(creds.credentials, settings.secret_key, algorithms=["HS256"])
        user_id = int(data["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        raise HTTPException(status_code=401, detail="Token inválido o expirado")

    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=401, detail="Usuario no encontrado")
    return user