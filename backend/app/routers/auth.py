import hmac
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models import OtpCode, User, utcnow
from app.schemas import CodeOut, PhoneIn, TokenOut, VerifyIn
from app.security import create_access_token, generate_otp, hash_otp

router = APIRouter(prefix="/auth", tags=["Autenticación"])


@router.post("/request-code", response_model=CodeOut)
def request_code(data: PhoneIn, db: Session = Depends(get_db)):
    """Paso 1: el usuario pide un código. En producción aquí se llamaría a un
    proveedor de SMS (Twilio, etc.); en el taller se simula imprimiéndolo en consola."""
    now = utcnow()

    # Limitación de tasa: 1 código por minuto por número (evita spam y abuso)
    last = db.scalar(
        select(OtpCode).where(OtpCode.phone == data.phone)
        .order_by(OtpCode.created_at.desc()).limit(1)
    )
    if last and (now - last.created_at).total_seconds() < 60:
        raise HTTPException(status_code=429, detail="Espera 60 segundos antes de pedir otro código")

    code = generate_otp()
    db.add(OtpCode(
        phone=data.phone,
        code_hash=hash_otp(data.phone, code),
        expires_at=now + timedelta(minutes=settings.otp_minutes),
    ))
    db.commit()

    print(f"[SMS SIMULADO] Código para {data.phone}: {code}")
    return CodeOut(
        message="Código enviado (simulado)",
        dev_code=code if settings.dev_mode else None,
    )


@router.post("/verify", response_model=TokenOut)
def verify_code(data: VerifyIn, db: Session = Depends(get_db)):
    """Paso 2: se valida el código y se entrega el token. Si el número es nuevo, se registra."""
    now = utcnow()
    otp = db.scalar(
        select(OtpCode).where(OtpCode.phone == data.phone, OtpCode.used.is_(False))
        .order_by(OtpCode.created_at.desc()).limit(1)
    )
    if otp is None or otp.expires_at < now:
        raise HTTPException(status_code=400, detail="Código inválido o expirado")
    if otp.attempts >= settings.otp_max_attempts:
        raise HTTPException(status_code=429, detail="Demasiados intentos; pide un código nuevo")

    otp.attempts += 1
    # compare_digest evita ataques de temporización al comparar hashes
    if not hmac.compare_digest(otp.code_hash, hash_otp(data.phone, data.code)):
        db.commit()  # guardamos el intento fallido antes de responder
        raise HTTPException(status_code=400, detail="Código inválido o expirado")

    otp.used = True  # un código solo sirve una vez
    user = db.scalar(select(User).where(User.phone == data.phone))
    if user is None:
        user = User(phone=data.phone)
        db.add(user)
        db.flush()  # obtiene el id sin cerrar la transacción
    db.commit()

    return TokenOut(access_token=create_access_token(user.id))