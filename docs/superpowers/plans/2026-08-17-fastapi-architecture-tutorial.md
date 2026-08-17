# FastAPI 架构入门 Word 教程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 生成一份面向零基础学习者、含架构图与代码图片的中文 FastAPI 架构 Word 教程。

**Architecture:** 使用一个可运行的 Todo API 贯穿全文。先在 `tutorial/fastapi_todo_example/` 写出可运行的分层示例项目，再由 `scripts/build_fastapi_tutorial.py` 将精选代码片段渲染为图片、绘制请求流程图、组装为 `.docx` 文档。成品文档只引用生成的本地图片，保持离线可阅读和编辑。

**Tech Stack:** Python 3、FastAPI、SQLAlchemy、Pydantic、PyJWT、Passlib、pytest、Docker、python-docx、Pillow。

## Global Constraints

- 成品必须使用简体中文，术语首次出现应有白话解释。
- 示例数据库固定为 SQLite，示例认证固定为 JWT Bearer Token。
- API 以用户只能操作本人待办事项为权限边界。
- 教程必须含封面、目录、架构图、代码图片、运行说明、测试说明和完整代码附录。
- 不修改 `src/`、`pom.xml` 或已有 Java 工程文件。

---

### Task 1: 建立可运行的 Todo API 骨架

**Files:**
- Create: `tutorial/fastapi_todo_example/requirements.txt`
- Create: `tutorial/fastapi_todo_example/app/main.py`
- Create: `tutorial/fastapi_todo_example/app/core/config.py`
- Create: `tutorial/fastapi_todo_example/app/core/database.py`
- Create: `tutorial/fastapi_todo_example/app/models/base.py`
- Test: `tutorial/fastapi_todo_example/tests/test_health.py`

**Interfaces:**
- Produces: `app.main:app`，一个可由 `TestClient` 调用的 `FastAPI` 实例。
- Produces: `GET /health` 返回 `{"status": "ok"}`。

- [ ] **Step 1: 写出健康检查的失败测试**

```python
from fastapi.testclient import TestClient
from app.main import app

def test_health_returns_ok():
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pytest tests/test_health.py::test_health_returns_ok -v`
Expected: FAIL，提示无法导入 `app.main`。

- [ ] **Step 3: 实现最小应用入口**

```python
from fastapi import FastAPI

app = FastAPI(title="Todo API")

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `pytest tests/test_health.py::test_health_returns_ok -v`
Expected: PASS。

- [ ] **Step 5: 提交骨架**

```bash
git add tutorial/fastapi_todo_example
git commit -m "feat: add FastAPI Todo API skeleton"
```

### Task 2: 实现数据模型、数据模式和仓储层

**Files:**
- Create: `tutorial/fastapi_todo_example/app/models/user.py`
- Create: `tutorial/fastapi_todo_example/app/models/todo.py`
- Create: `tutorial/fastapi_todo_example/app/schemas/user.py`
- Create: `tutorial/fastapi_todo_example/app/schemas/todo.py`
- Create: `tutorial/fastapi_todo_example/app/repositories/user_repository.py`
- Create: `tutorial/fastapi_todo_example/app/repositories/todo_repository.py`
- Test: `tutorial/fastapi_todo_example/tests/test_todo_repository.py`

**Interfaces:**
- Consumes: `get_db() -> Generator[Session, None, None]`。
- Produces: `TodoRepository.create(db: Session, owner_id: int, title: str) -> Todo`。
- Produces: `TodoRepository.list_by_owner(db: Session, owner_id: int) -> list[Todo]`。

- [ ] **Step 1: 写出仓储层失败测试**

```python
def test_repository_only_returns_owner_todos(db_session):
    first = TodoRepository.create(db_session, owner_id=1, title="学习 FastAPI")
    TodoRepository.create(db_session, owner_id=2, title="他人的任务")
    todos = TodoRepository.list_by_owner(db_session, owner_id=1)
    assert [todo.id for todo in todos] == [first.id]
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pytest tests/test_todo_repository.py::test_repository_only_returns_owner_todos -v`
Expected: FAIL，提示 `TodoRepository` 不存在。

- [ ] **Step 3: 实现最小数据库读取逻辑**

```python
class TodoRepository:
    @staticmethod
    def list_by_owner(db: Session, owner_id: int) -> list[Todo]:
        return list(db.scalars(select(Todo).where(Todo.owner_id == owner_id)))
```

- [ ] **Step 4: 运行测试确认通过**

Run: `pytest tests/test_todo_repository.py::test_repository_only_returns_owner_todos -v`
Expected: PASS。

- [ ] **Step 5: 提交数据访问层**

```bash
git add tutorial/fastapi_todo_example
git commit -m "feat: add Todo persistence layer"
```

### Task 3: 实现注册、登录和 JWT 认证依赖

**Files:**
- Create: `tutorial/fastapi_todo_example/app/core/security.py`
- Create: `tutorial/fastapi_todo_example/app/services/auth_service.py`
- Create: `tutorial/fastapi_todo_example/app/dependencies/auth.py`
- Create: `tutorial/fastapi_todo_example/app/routers/auth.py`
- Modify: `tutorial/fastapi_todo_example/app/main.py`
- Test: `tutorial/fastapi_todo_example/tests/test_auth.py`

**Interfaces:**
- Produces: `POST /auth/register`，入参 `UserCreate`，状态码 `201`。
- Produces: `POST /auth/login`，入参 `LoginRequest`，返回 `TokenResponse(access_token, token_type="bearer")`。
- Produces: `get_current_user(token: str, db: Session) -> User`，无效 Token 时抛出 401。

- [ ] **Step 1: 写出认证流程失败测试**

```python
def test_registered_user_can_log_in(client):
    client.post("/auth/register", json={"email": "ada@example.com", "password": "secret123"})
    response = client.post("/auth/login", json={"email": "ada@example.com", "password": "secret123"})
    assert response.status_code == 200
    assert response.json()["token_type"] == "bearer"
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pytest tests/test_auth.py::test_registered_user_can_log_in -v`
Expected: FAIL，返回 404 或路由未注册。

- [ ] **Step 3: 实现密码哈希和令牌签发**

```python
def create_access_token(user_id: int) -> str:
    payload = {"sub": str(user_id), "exp": datetime.now(timezone.utc) + timedelta(minutes=60)}
    return jwt.encode(payload, settings.secret_key, algorithm="HS256")
```

- [ ] **Step 4: 运行测试确认通过**

Run: `pytest tests/test_auth.py::test_registered_user_can_log_in -v`
Expected: PASS。

- [ ] **Step 5: 提交认证功能**

```bash
git add tutorial/fastapi_todo_example
git commit -m "feat: add JWT authentication"
```

### Task 4: 实现受保护的 Todo 服务与路由

**Files:**
- Create: `tutorial/fastapi_todo_example/app/services/todo_service.py`
- Create: `tutorial/fastapi_todo_example/app/routers/todos.py`
- Modify: `tutorial/fastapi_todo_example/app/main.py`
- Test: `tutorial/fastapi_todo_example/tests/test_todos.py`

**Interfaces:**
- Produces: `POST /todos`、`GET /todos`、`PATCH /todos/{todo_id}`、`DELETE /todos/{todo_id}`。
- Produces: `TodoService.update(db: Session, current_user: User, todo_id: int, data: TodoUpdate) -> Todo`，他人资源或不存在的资源均返回 404。

- [ ] **Step 1: 写出所有权隔离失败测试**

```python
def test_user_cannot_update_another_users_todo(client, token_a, token_b):
    created = client.post("/todos", headers={"Authorization": f"Bearer {token_a}"}, json={"title": "私有任务"})
    todo_id = created.json()["id"]
    response = client.patch(f"/todos/{todo_id}", headers={"Authorization": f"Bearer {token_b}"}, json={"completed": True})
    assert response.status_code == 404
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pytest tests/test_todos.py::test_user_cannot_update_another_users_todo -v`
Expected: FAIL，返回 404 以外的状态或路由缺失。

- [ ] **Step 3: 在服务层实现所有权检查**

```python
todo = TodoRepository.get_by_id_and_owner(db, todo_id=todo_id, owner_id=current_user.id)
if todo is None:
    raise HTTPException(status_code=404, detail="待办事项不存在")
```

- [ ] **Step 4: 运行测试确认通过**

Run: `pytest tests/test_todos.py::test_user_cannot_update_another_users_todo -v`
Expected: PASS。

- [ ] **Step 5: 提交 Todo API**

```bash
git add tutorial/fastapi_todo_example
git commit -m "feat: add protected Todo CRUD"
```

### Task 5: 添加异常展示、Docker 与教程配图素材

**Files:**
- Create: `tutorial/fastapi_todo_example/Dockerfile`
- Create: `tutorial/fastapi_todo_example/.dockerignore`
- Create: `tutorial/fastapi_todo_example/tests/test_errors.py`
- Create: `tutorial/fastapi_tutorial_assets/architecture-flow.png`
- Create: `tutorial/fastapi_tutorial_assets/project-tree.png`
- Create: `tutorial/fastapi_tutorial_assets/code-*.png`

**Interfaces:**
- Consumes: 完整 FastAPI 示例项目。
- Produces: 可用于 Word 的 PNG 架构图、项目树图和核心代码图片。
- Produces: `docker build -t fastapi-todo-api .` 可构建的镜像定义。

- [ ] **Step 1: 写出无令牌访问的失败测试**

```python
def test_todo_list_requires_bearer_token(client):
    response = client.get("/todos")
    assert response.status_code == 401
```

- [ ] **Step 2: 运行测试确认失败**

Run: `pytest tests/test_errors.py::test_todo_list_requires_bearer_token -v`
Expected: FAIL，返回 200 或路由尚未实现。

- [ ] **Step 3: 增加认证依赖与 Docker 定义**

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

- [ ] **Step 4: 运行全部测试并验证图片尺寸**

Run: `pytest -v`
Expected: PASS。

Run: `python -c "from PIL import Image; print(Image.open('tutorial/fastapi_tutorial_assets/architecture-flow.png').size)"`
Expected: 输出宽度不小于 1200 像素。

- [ ] **Step 5: 提交可部署示例与素材**

```bash
git add tutorial
git commit -m "docs: add FastAPI tutorial assets"
```

### Task 6: 生成与检查 Word 教程

**Files:**
- Create: `scripts/build_fastapi_tutorial.py`
- Create: `deliverables/FastAPI_架构零基础教程.docx`
- Test: `scripts/test_fastapi_tutorial_document.py`

**Interfaces:**
- Consumes: `tutorial/fastapi_todo_example/` 和 `tutorial/fastapi_tutorial_assets/`。
- Produces: `deliverables/FastAPI_架构零基础教程.docx`，含至少 9 个一级章节、图片、代码附录。

- [ ] **Step 1: 写出文档结构失败测试**

```python
from docx import Document

def test_document_has_required_headings():
    document = Document("deliverables/FastAPI_架构零基础教程.docx")
    headings = [p.text for p in document.paragraphs if p.style.name.startswith("Heading 1")]
    assert "FastAPI 是什么" in headings
    assert "Docker 部署" in headings
    assert "完整项目代码附录" in headings
```

- [ ] **Step 2: 运行测试确认失败**

Run: `python scripts/test_fastapi_tutorial_document.py`
Expected: FAIL，提示目标 `.docx` 文件不存在。

- [ ] **Step 3: 实现 Word 生成器**

```python
document = Document()
document.add_heading("FastAPI 架构零基础教程", 0)
document.add_heading("FastAPI 是什么", level=1)
document.add_picture(assets / "architecture-flow.png", width=Inches(6.2))
document.save(output_path)
```

- [ ] **Step 4: 生成文档并运行结构测试**

Run: `python scripts/build_fastapi_tutorial.py`
Expected: 成功输出 `.docx`。

Run: `python scripts/test_fastapi_tutorial_document.py`
Expected: PASS。

- [ ] **Step 5: 提交最终文档**

```bash
git add scripts/build_fastapi_tutorial.py scripts/test_fastapi_tutorial_document.py deliverables/FastAPI_架构零基础教程.docx
git commit -m "docs: publish FastAPI architecture tutorial"
```

## Self-review

- 覆盖检查：任务 1 至 4 交付单文件起步、分层 CRUD、SQLite、JWT 与权限；任务 5 交付错误处理、Docker 和配图；任务 6 交付 Word 教程、目录结构检查和附录。
- 占位检查：本计划不含任何未完成标记、延后实现表述或未定义的接口占位语。
- 接口检查：任务 2 的 `TodoRepository` 被任务 4 消费；任务 3 的认证依赖被任务 4 和任务 5 消费；任务 6 只消费前置任务生成的示例项目和图片资源。
