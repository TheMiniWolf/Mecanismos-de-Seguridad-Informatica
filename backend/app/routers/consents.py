from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import Consent, User, utcnow
from app.schemas import ConsentOut, PhoneIn
from app.security import get_current_user

router = APIRouter(prefix="/consents", tags=["Consentimientos"])

# Máquina de estados simplificada (inspirada en el patrón State):
# cada acción define quién puede ejecutarla, desde qué estados y a cuál se llega.
TRANSITIONS = {
    "accept": {"who": "target", "from": {"pending"}, "to": "accepted"},
    "reject": {"who": "target", "from": {"pending"}, "to": "rejected"},
    # Revocar lo puede hacer cualquiera de las dos partes, en cualquier momento
    "revoke": {"who": "either", "from": {"pending", "accepted"}, "to": "revoked"},
}


def _to_out(c: Consent) -> ConsentOut:
    return ConsentOut(
        id=c.id,
        observer_id=c.observer_id, observer_phone=c.observer.phone,
        target_id=c.target_id, target_phone=c.target.phone,
        status=c.status, created_at=c.created_at,
    )


def _transition(db: Session, consent_id: int, user: User, action: str) -> ConsentOut:
    rule = TRANSITIONS[action]
    c = db.get(Consent, consent_id)
    if c is None:
        raise HTTPException(status_code=404, detail="Solicitud no encontrada")

    allowed = {c.target_id} if rule["who"] == "target" else {c.target_id, c.observer_id}
    if user.id not in allowed:
        raise HTTPException(status_code=403, detail="No puedes realizar esta acción")
    if c.status not in rule["from"]:
        raise HTTPException(status_code=409, detail=f"La solicitud está en estado '{c.status}'")

    c.status = rule["to"]
    c.updated_at = utcnow()
    db.commit()
    db.refresh(c)
    return _to_out(c)


@router.post("", response_model=ConsentOut, status_code=201)
def request_consent(data: PhoneIn, user: User = Depends(get_current_user),
                    db: Session = Depends(get_db)):
    """El observador pide permiso para ver a otra persona (que ya debe estar registrada).
    Nota para el informe: revelar si un número existe permite enumerar usuarios;
    es un riesgo conocido y aceptado en este prototipo."""
    target = db.scalar(select(User).where(User.phone == data.phone))
    if target is None:
        raise HTTPException(status_code=404, detail="No hay un usuario registrado con ese número")
    if target.id == user.id:
        raise HTTPException(status_code=400, detail="No puedes autorizarte a ti mismo")

    existing = db.scalar(select(Consent).where(
        Consent.observer_id == user.id, Consent.target_id == target.id))
    if existing:
        if existing.status in ("pending", "accepted"):
            raise HTTPException(status_code=409, detail="Ya existe una solicitud activa")
        existing.status = "pending"        # se re-solicita tras un rechazo o revocación
        existing.updated_at = utcnow()
        consent = existing
    else:
        consent = Consent(observer_id=user.id, target_id=target.id)
        db.add(consent)

    db.commit()
    db.refresh(consent)
    return _to_out(consent)


@router.get("/incoming", response_model=list[ConsentOut])
def incoming(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Solicitudes que otros me hicieron a mí (yo decido si acepto)."""
    rows = db.scalars(select(Consent).where(Consent.target_id == user.id)
                      .order_by(Consent.created_at.desc()))
    return [_to_out(c) for c in rows]


@router.get("/outgoing", response_model=list[ConsentOut])
def outgoing(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Solicitudes que yo hice a otros."""
    rows = db.scalars(select(Consent).where(Consent.observer_id == user.id)
                      .order_by(Consent.created_at.desc()))
    return [_to_out(c) for c in rows]


@router.post("/{consent_id}/accept", response_model=ConsentOut)
def accept(consent_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _transition(db, consent_id, user, "accept")


@router.post("/{consent_id}/reject", response_model=ConsentOut)
def reject(consent_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _transition(db, consent_id, user, "reject")


@router.post("/{consent_id}/revoke", response_model=ConsentOut)
def revoke(consent_id: int, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _transition(db, consent_id, user, "revoke")