from fastapi import FastAPI

from app.core.database import engine
from app.models.base import Base
from app.models import todo, user  # noqa: F401 - import models before creating tables
from app.routers import auth, todos


app = FastAPI(
    title="Todo API",
    description="供 FastAPI 架构教学使用的待办事项接口。",
    version="1.0.0",
)
Base.metadata.create_all(bind=engine)
app.include_router(auth.router)
app.include_router(todos.router)


@app.get("/health", tags=["系统"])
def health() -> dict[str, str]:
    """提供无需认证的存活检查。"""
    return {"status": "ok"}
