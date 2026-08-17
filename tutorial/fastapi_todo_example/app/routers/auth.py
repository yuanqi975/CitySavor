from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.schemas.user import LoginRequest, TokenResponse, UserCreate, UserRead
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth", tags=["认证"])


@router.post("/register", response_model=UserRead, status_code=status.HTTP_201_CREATED)
def register(data: UserCreate, db: Session = Depends(get_db)) -> UserRead:
    return AuthService.register(db, email=data.email, password=data.password)


@router.post("/login", response_model=TokenResponse)
def login(data: LoginRequest, db: Session = Depends(get_db)) -> TokenResponse:
    return TokenResponse(access_token=AuthService.login(db, email=data.email, password=data.password))
