#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成用于端到端测试的样本文件: sample/sample.pdf 与 sample/sample.docx
- PDF: 标准 Helvetica 字体仅支持 Latin, 故用英文短语
- DOCX: 原生 UTF-8 XML, 用中文短语(用于验证中文提取与检索)
无需任何第三方库, 仅用标准库。
运行: python3 scripts/gen_samples.py
"""
import os
import struct
import zipfile

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SAMPLE_DIR = os.path.join(BASE, "sample")
os.makedirs(SAMPLE_DIR, exist_ok=True)


def escape_pdf_string(s):
    # PDF 字符串用括号包裹, 需转义反斜杠与括号
    return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def build_pdf(lines):
    """构建一个单页、含多行文本的合法 PDF(含正确 xref 偏移)。"""
    content_lines = ["BT", "/F1 18 Tf", "72 720 Td", "24 TL"]
    for i, line in enumerate(lines):
        if i == 0:
            content_lines.append(f"({escape_pdf_string(line)}) Tj")
        else:
            content_lines.append(f"T* ({escape_pdf_string(line)}) Tj")
    content_lines.append("ET")
    content = "\n".join(content_lines).encode("latin-1")

    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length " + str(len(content)).encode() + b" >>\nstream\n" + content + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]

    pdf = b"%PDF-1.4\n"
    offsets = []
    for i, obj in enumerate(objects, start=1):
        offsets.append(len(pdf))
        pdf += f"{i} 0 obj\n".encode("latin-1") + obj + b"\nendobj\n"

    xref_pos = len(pdf)
    n = len(objects) + 1
    pdf += b"xref\n"
    pdf += f"0 {n}\n".encode("latin-1")
    pdf += b"0000000000 65535 f \r\n"
    for off in offsets:
        pdf += f"{off:010d} 00000 n \r\n".encode("latin-1")
    pdf += b"trailer\n"
    pdf += b"<< /Size " + str(n).encode("latin-1") + b" /Root 1 0 R >>\n"
    pdf += b"startxref\n"
    pdf += str(xref_pos).encode("latin-1") + b"\n"
    pdf += b"%%EOF"
    return pdf


def build_docx(text):
    """构建一个最小的合法 .docx(OOXML), 正文为给定文本。"""
    content_types = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" '
        'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
        '</Types>'
    )
    rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" '
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
        'Target="word/document.xml"/>'
        '</Relationships>'
    )
    # 每行文本做成一个段落
    paragraphs = "".join(
        f'<w:p><w:r><w:t xml:space="preserve">{line}</w:t></w:r></w:p>'
        for line in text.split("\n")
    )
    document = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        '<w:body>'
        + paragraphs
        + "</w:body></w:document>"
    )

    buf = zipfile.ZipFile(os.path.join(SAMPLE_DIR, "sample.docx"), "w", zipfile.ZIP_DEFLATED)
    with buf:
        buf.writestr("[Content_Types].xml", content_types)
        buf.writestr("_rels/.rels", rels)
        buf.writestr("word/document.xml", document)


def main():
    pdf_lines = [
        "Sample PDF for Text Extraction",
        "Contract Amount: 10000 CNY",
        "Party A Signature: Zhang San",
        "Signed in 2024",
        "Confidential Document",
    ]
    with open(os.path.join(SAMPLE_DIR, "sample.pdf"), "wb") as f:
        f.write(build_pdf(pdf_lines))

    docx_text = (
        "这是一份用于测试的 Word 文档。\n"
        "合同金额：人民币壹万元整。\n"
        "甲方签字：张三\n"
        "乙方签字：李四\n"
        "签订日期：2024年\n"
        "本文件为机密文档。"
    )
    build_docx(docx_text)

    print("generated:")
    for name in ("sample.pdf", "sample.docx"):
        p = os.path.join(SAMPLE_DIR, name)
        print(f"  {p}  ({os.path.getsize(p)} bytes)")


if __name__ == "__main__":
    main()
