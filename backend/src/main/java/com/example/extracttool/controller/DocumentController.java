package com.example.extracttool.controller;

import com.example.extracttool.dto.DocumentSummary;
import com.example.extracttool.entity.ExtractedDocument;
import com.example.extracttool.repository.ExtractedDocumentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 已缓存文档的查询接口(供前端选择检索来源等)。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final ExtractedDocumentRepository repo;

    public DocumentController(ExtractedDocumentRepository repo) {
        this.repo = repo;
    }

    /** 列出全部缓存文档(不含全文) */
    @GetMapping
    public List<DocumentSummary> list() {
        return repo.findAllByOrderByUpdatedAtDesc().stream()
                .map(d -> toSummary(d, false))
                .collect(Collectors.toList());
    }

    /** 查看某个缓存文档(含全文) */
    @GetMapping("/{id}")
    public DocumentSummary get(@PathVariable Long id) {
        ExtractedDocument doc = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: id=" + id));
        return toSummary(doc, true);
    }

    private DocumentSummary toSummary(ExtractedDocument doc, boolean withText) {
        DocumentSummary s = new DocumentSummary();
        s.setId(doc.getId());
        s.setFileName(doc.getFileName());
        s.setFileType(doc.getFileType());
        s.setFileSize(doc.getFileSize());
        s.setContentHash(doc.getContentHash());
        s.setTextLength(doc.getExtractedText() == null ? 0 : doc.getExtractedText().length());
        s.setUpdatedAt(doc.getUpdatedAt());
        if (withText) {
            s.setExtractedText(doc.getExtractedText());
        }
        return s;
    }
}
