from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import Consent, Location, User, utcnow
from app.schemas import LocationIn, LocationOut, TargetOut
from app.security import get_current_user

router = APIRouter(tags=["Ubicaciones"])


def _history(db: Session, user_id: int, desde: datetime | None,
             hasta: datetime | None, limit: int) -> list[Location]:
    """Consulta reutilizable de historial (la usan el propio usuario y sus observadores)."""
    stmt = select(Location).where(Location.user_id == user_id)
    if desde:
        stmt = stmt.where(Location.recorded_at >= desde)
    if hasta:
        stmt = stmt.where(Location.recorded_at <= hasta)
    return list(db.scalars(stmt.order_by(Location.recorded_at.desc()).limit(limit)))


def _require_consent(db: Session, observer_id: int, target_id: int) -> None:
    """Control de acceso central: sin consentimiento aceptado NO se entrega ningún dato."""
    ok = db.scalar(select(Consent).where(
        Consent.observer_id == observer_id,
        Consent.target_id == target_id,
        Consent.status == "accepted"))
    if ok is None:
        raise HTTPException(status_code=403, detail="No tienes autorización de ese usuario")


# ---------- Lado del usuario que comparte su ubicación ----------
@router.post("/locations", response_model=LocationOut, status_code=201)
def send_location(data: LocationIn, user: User = Depends(get_current_user),
                  db: Session = Depends(get_db)):
    """La app Android llama aquí cada intervalo (ej. 10 min). Solo guarda ubicaciones PROPIAS:
    el user_id sale del token, nunca del cuerpo de la petición."""
    now = utcnow()
    recorded = data.recorded_at or now
    if recorded.tzinfo is None:                       # si llega sin zona, asumimos UTC
        recorded = recorded.replace(tzinfo=timezone.utc)
    if recorded > now + timedelta(minutes=5):         # rechaza fechas del futuro
        raise HTTPException(status_code=422, detail="recorded_at no puede estar en el futuro")

    loc = Location(user_id=user.id, latitude=data.latitude, longitude=data.longitude,
                   accuracy=data.accuracy, recorded_at=recorded)
    db.add(loc)
    db.commit()
    db.refresh(loc)
    return loc


@router.get("/locations/me", response_model=list[LocationOut])
def my_history(desde: datetime | None = None, hasta: datetime | None = None,
               limit: int = Query(200, ge=1, le=1000),
               user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _history(db, user.id, desde, hasta, limit)


@router.delete("/locations/me")
def delete_my_history(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Derecho al borrado: el usuario puede eliminar todo su historial cuando quiera."""
    result = db.execute(delete(Location).where(Location.user_id == user.id))
    db.commit()
    return {"deleted": result.rowcount}


# ---------- Lado del observador (requiere consentimiento aceptado) ----------
@router.get("/targets", response_model=list[TargetOut])
def my_targets(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Personas que me autorizaron a ver su ubicación (lista para el panel del mapa)."""
    rows = db.scalars(select(Consent).where(
        Consent.observer_id == user.id, Consent.status == "accepted"))
    return [TargetOut(user_id=c.target_id, phone=c.target.phone) for c in rows]


@router.get("/targets/{target_id}/last", response_model=LocationOut)
def target_last(target_id: int, user: User = Depends(get_current_user),
                db: Session = Depends(get_db)):
    _require_consent(db, user.id, target_id)
    rows = _history(db, target_id, None, None, 1)
    if not rows:
        raise HTTPException(status_code=404, detail="Aún no hay ubicaciones registradas")
    return rows[0]


@router.get("/targets/{target_id}/history", response_model=list[LocationOut])
def target_history(target_id: int, desde: datetime | None = None, hasta: datetime | None = None,
                   limit: int = Query(200, ge=1, le=1000),
                   user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    _require_consent(db, user.id, target_id)
    return _history(db, target_id, desde, hasta, limit)