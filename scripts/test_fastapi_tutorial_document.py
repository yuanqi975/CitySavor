from pathlib import Path

from docx import Document


OUTPUT = Path("deliverables/FastAPI_架构零基础教程.docx")
REQUIRED_HEADINGS = {
    "FastAPI 是什么",
    "从单文件到分层架构",
    "数据库与 SQLAlchemy",
    "JWT 登录与权限控制",
    "Docker 部署",
    "完整项目代码附录",
}


def test_document_has_required_sections_and_images() -> None:
    """Removing a core lesson or its visual material must fail this publishing check."""
    document = Document(OUTPUT)
    heading_text = {paragraph.text for paragraph in document.paragraphs if paragraph.style.name.startswith("Heading 1")}

    assert REQUIRED_HEADINGS <= heading_text
    assert len(document.inline_shapes) >= 7


if __name__ == "__main__":
    test_document_has_required_sections_and_images()
    print("Document structure check passed.")
