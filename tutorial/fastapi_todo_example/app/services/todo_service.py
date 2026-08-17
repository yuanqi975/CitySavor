from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.models.todo import Todo
from app.models.user import User
from app.repositories.todo_repository import TodoRepository
from app.schemas.todo import TodoUpdate


class TodoService:
    @staticmethod
    def create(db: Session, current_user: User, title: str) -> Todo:
        return TodoRepository.create(db, owner_id=current_user.id, title=title)

    @staticmethod
    def list(db: Session, current_user: User) -> list[Todo]:
        return TodoRepository.list_by_owner(db, owner_id=current_user.id)

    @staticmethod
    def update(db: Session, current_user: User, todo_id: int, data: TodoUpdate) -> Todo:
        todo = TodoRepository.get_by_id_and_owner(db, todo_id=todo_id, owner_id=current_user.id)
        if todo is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="待办事项不存在")
        if data.title is not None:
            todo.title = data.title
        if data.completed is not None:
            todo.completed = data.completed
        db.commit()
        db.refresh(todo)
        return todo

    @staticmethod
    def delete(db: Session, current_user: User, todo_id: int) -> None:
        todo = TodoRepository.get_by_id_and_owner(db, todo_id=todo_id, owner_id=current_user.id)
        if todo is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="待办事项不存在")
        TodoRepository.delete(db, todo)
