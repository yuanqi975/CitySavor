from sqlalchemy import Boolean, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base


class Todo(Base):
    __tablename__ = "todos"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    completed: Mapped[bool] = mapped_column(Boolean, default=False)
    owner_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    owner: Mapped["User"] = relationship(back_populates="todos")
