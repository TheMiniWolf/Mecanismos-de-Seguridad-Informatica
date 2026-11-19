from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from app.config import settings

# pool_pre_ping verifica que la conexión siga viva antes de usarla
engine = create_engine(settings.database_url, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False)


class Base(DeclarativeBase):
    """Clase base de todos los modelos ORM."""


def get_db():
    """Dependencia de FastAPI (patrón Dependency Injection).
    Abre una sesión por petición y garantiza que se cierre al terminar,
    incluso si ocurre un error."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()