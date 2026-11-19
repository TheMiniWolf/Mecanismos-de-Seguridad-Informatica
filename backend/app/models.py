from datetime import datetime, timezone

from sqlalchemy import (BigInteger, Boolean, DateTime, Float, ForeignKey, Index,
                        Integer, String, UniqueConstraint)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


def utcnow() -> datetime:
    """Siempre guardamos fechas en UTC con zona horaria (evita errores de husos)."""
    return datetime.now(timezone.utc)


class User(Base):
    """Usuario identificado por su número en formato E.164 (+573001234567).
    Solo se crea tras verificar el código OTP, por eso no hace falta 'is_verified'."""
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    phone: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class OtpCode(Base):
    """Códigos de verificación por SMS. Se guarda el HASH, nunca el código en claro."""
    __tablename__ = "otp_codes"

    id: Mapped[int] = mapped_column(primary_key=True)
    phone: Mapped[str] = mapped_column(String(20), index=True)
    code_hash: Mapped[str] = mapped_column(String(64))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    used: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Consent(Base):
    """Autorización: el 'observer' quiere ver la ubicación del 'target'.
    Estados: pending -> accepted | rejected ; accepted/pending -> revoked."""
    __tablename__ = "consents"
    __table_args__ = (UniqueConstraint("observer_id", "target_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    observer_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    target_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    status: Mapped[str] = mapped_column(String(10), default="pending")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    # Relaciones para acceder a los teléfonos sin consultas manuales
    observer: Mapped["User"] = relationship(foreign_keys=[observer_id])
    target: Mapped["User"] = relationship(foreign_keys=[target_id])


class Location(Base):
    """Cada punto GPS enviado por el dispositivo del usuario."""
    __tablename__ = "locations"
    # Índice compuesto: acelera "historial de un usuario ordenado por fecha"
    __table_args__ = (Index("ix_locations_user_time", "user_id", "recorded_at"),)

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    latitude: Mapped[float] = mapped_column(Float)
    longitude: Mapped[float] = mapped_column(Float)
    accuracy: Mapped[float | None] = mapped_column(Float, nullable=True)   # metros
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))  # hora en el celular
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)