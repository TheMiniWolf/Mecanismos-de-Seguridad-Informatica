from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles

from app import models  # noqa: F401  (importarlo registra las tablas en Base.metadata)
from app.config import settings
from app.database import Base, engine
from app.routers import auth, consents, locations

# Crea las tablas si no existen. Es válido para el taller; en producción se usaría Alembic.
Base.metadata.create_all(bind=engine)

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"

# Texto que acompaña a cada grupo de endpoints en la documentación
TAGS = [
    {"name": "Autenticación",
     "description": "Entrar con el número de teléfono y un código de un solo uso. No necesita token."},
    {"name": "Consentimientos",
     "description": "Pedir, aceptar, rechazar o retirar el permiso para ver la ubicación de otra persona."},
    {"name": "Ubicaciones",
     "description": "Enviar tu ubicación, ver tu historial y consultar a quienes te dieron permiso."},
    {"name": "Sistema", "description": "Comprobar que el servidor está encendido."},
]

app = FastAPI(
    title="Faro · API de localización con consentimiento",
    description="API académica: registro por teléfono (OTP), consentimiento explícito y ubicaciones.",
    version="1.0.0",
    openapi_tags=TAGS,
    docs_url=None,    # se apaga la página /docs por defecto: abajo se sirve una propia
    redoc_url=None,
)

# CORS abierto solo en desarrollo. La autenticación va en el header Authorization
# (no en cookies), por eso es aceptable en este entorno de laboratorio.
if settings.dev_mode:
    app.add_middleware(CORSMiddleware, allow_origins=["*"],
                       allow_methods=["*"], allow_headers=["*"])

# Patrón Router: cada módulo agrupa sus endpoints y se "enchufa" aquí
app.include_router(auth.router)
app.include_router(consents.router)
app.include_router(locations.router)


@app.get("/health", tags=["Sistema"])
def health():
    return {"status": "ok"}


@app.get("/", include_in_schema=False)
def root():
    """La raíz redirige al panel web."""
    return RedirectResponse(url="/panel/")


@app.get("/docs", include_in_schema=False)
def docs():
    """Documentación interactiva con diseño propio (static/docs.html)."""
    return FileResponse(STATIC_DIR / "docs.html")


# ----------------------------------------------------------------------
# Títulos y explicaciones en español para la documentación.
# Se aplican aquí, en un solo lugar, sin tocar los archivos de los routers:
# (método, ruta) -> (título corto, explicación)
# ----------------------------------------------------------------------
DOCS = {
    ("POST", "/auth/request-code"): (
        "Paso 1: pedir el código",
        "Envía tu número con + y código de país. En el laboratorio el SMS es simulado: "
        "el código llega en `dev_code`. Vence a los 5 minutos y solo se puede pedir uno por minuto."),
    ("POST", "/auth/verify"): (
        "Paso 2: entrar con el código",
        "Devuelve el `access_token`. Cópialo y pégalo en el botón **Authorize**. "
        "Si el número es nuevo, se crea la cuenta."),
    ("POST", "/consents"): (
        "Pedir permiso para ver a alguien",
        "Escribe el número de la otra persona. La solicitud queda `pending` hasta que ella la acepte."),
    ("GET", "/consents/incoming"): (
        "Ver quién quiere ver mi ubicación",
        "Solicitudes que otras personas me hicieron. Aquí sale el `id` que se usa para aceptar o rechazar."),
    ("GET", "/consents/outgoing"): (
        "Ver los permisos que pedí",
        "Solicitudes que yo hice y su estado: pending, accepted, rejected o revoked."),
    ("POST", "/consents/{consent_id}/accept"): (
        "Aceptar una solicitud",
        "Solo puede hacerlo la persona a la que quieren ver. Desde ese momento el observador ve su ubicación."),
    ("POST", "/consents/{consent_id}/reject"): (
        "Rechazar una solicitud",
        "Solo puede hacerlo la persona a la que quieren ver."),
    ("POST", "/consents/{consent_id}/revoke"): (
        "Retirar un permiso",
        "Cualquiera de las dos partes puede hacerlo en cualquier momento. El acceso se corta de inmediato."),
    ("POST", "/locations"): (
        "Enviar mi ubicación",
        "Guarda un punto a nombre del dueño del token. Es lo que la app Android llama cada intervalo."),
    ("GET", "/locations/me"): (
        "Ver mi historial",
        "Mis puntos guardados, del más reciente al más antiguo. `desde` y `hasta` filtran por fecha."),
    ("DELETE", "/locations/me"): (
        "Borrar todo mi historial",
        "Elimina todos mis puntos del servidor. No se puede deshacer."),
    ("GET", "/targets"): (
        "Ver a quién puedo ver",
        "Personas que aceptaron mi solicitud. Su `user_id` es el `target_id` de los dos endpoints siguientes."),
    ("GET", "/targets/{target_id}/last"): (
        "Última ubicación de una persona",
        "Responde 403 si esa persona no me ha dado permiso o me lo retiró."),
    ("GET", "/targets/{target_id}/history"): (
        "Recorrido de una persona",
        "Sus puntos, del más reciente al más antiguo. Responde 403 sin permiso aceptado."),
    ("GET", "/health"): (
        "Comprobar el servidor",
        "Responde `{\"status\": \"ok\"}` si el backend está encendido."),
}

_openapi_original = app.openapi


def openapi_es():
    """Genera el esquema OpenAPI normal y le pone los títulos y explicaciones de DOCS."""
    schema = _openapi_original()   # FastAPI lo guarda en caché: solo se construye una vez
    for path, operations in schema.get("paths", {}).items():
        for method, operation in operations.items():
            texts = DOCS.get((method.upper(), path))
            if texts:
                operation["summary"], operation["description"] = texts
    return schema


app.openapi = openapi_es   # /openapi.json y la página /docs usan ahora esta versión


# Panel web estático (HTML + JS) servido por el mismo backend.
# Al estar en el mismo origen que la API, el navegador no tiene problemas de CORS.
app.mount("/panel", StaticFiles(directory=STATIC_DIR, html=True), name="panel")