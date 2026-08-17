from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """所有数据库表共用的 SQLAlchemy 基类。"""
