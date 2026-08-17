from fastapi import APIRouter, Depends, Response, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.todo import TodoCreate, TodoRead, TodoUpdate
from app.services.todo_service import TodoService

router = APIRouter(prefix="/todos", tags=["待办事项"])


@router.post("", response_model=TodoRead, status_code=status.HTTP_201_CREATED)
def create_todo(
    data: TodoCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> TodoRead:
    return TodoService.create(db, current_user, data.title)


@router.get("", response_model=list[TodoRead])
def list_todos(
    db: Session = Depends(get_db), current_user: User = Depends(get_current_user)
) -> list[TodoRead]:
    return TodoService.list(db, current_user)


@router.patch("/{todo_id}", response_model=TodoRead)
def update_todo(
    todo_id: int,
    data: TodoUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> TodoRead:
    return TodoService.update(db, current_user, todo_id, data)


@router.delete("/{todo_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_todo(
    todo_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Response:
    TodoService.delete(db, current_user, todo_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
