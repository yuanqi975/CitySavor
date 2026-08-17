from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.security import create_access_token, hash_password, verify_password
from app.models.user import User
from app.repositories.user_repository import UserRepository


class AuthService:
    @staticmethod
    def register(db: Session, email: str, password: str) -> User:
        if UserRepository.get_by_email(db, email) is not None:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="邮箱已注册")
        return UserRepository.create(db, email=email, password_hash=hash_password(password))

    @staticmethod
    def login(db: Session, email: str, password: str) -> str:
        user = UserRepository.get_by_email(db, email)
        if user is None or not verify_password(password, user.password_hash):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="邮箱或密码错误")
        return create_access_token(user.id)
