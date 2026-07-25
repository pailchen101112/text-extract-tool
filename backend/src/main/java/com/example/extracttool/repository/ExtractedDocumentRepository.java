package com.example.extracttool.repository;

import com.example.extracttool.entity.ExtractedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExtractedDocumentRepository extends JpaRepository<ExtractedDocument, Long> {

    /** 按文件名查询缓存(缓存命中入口) */
    Optional<ExtractedDocument> findByFileName(String fileName);

    /** 按更新时间倒序列出全部缓存 */
    List<ExtractedDocument> findAllByOrderByUpdatedAtDesc();
}
