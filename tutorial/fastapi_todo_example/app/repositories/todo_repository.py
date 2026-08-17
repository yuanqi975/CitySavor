from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.todo import Todo


class TodoRepository:
    @staticmethod
    def create(db: Session, owner_id: int, title: str) -> Todo:
        todo = Todo(owner_id=owner_id, title=title)
        db.add(todo)
        db.commit()
        db.refresh(todo)
        return todo

    @staticmethod
    def list_by_owner(db: Session, owner_id: int) -> list[Todo]:
        return list(db.scalars(select(Todo).where(Todo.owner_id == owner_id).order_by(Todo.id)))

    @staticmethod
    def get_by_id_and_owner(db: Session, todo_id: int, owner_id: int) -> Todo | None:
        return db.scalar(select(Todo).where(Todo.id == todo_id, Todo.owner_id == owner_id))

    @staticmethod
    def delete(db: Session, todo: Todo) -> None:
        db.delete(todo)
        db.commit()
