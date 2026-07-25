package com.example.extracttool.dto;

import java.time.LocalDateTime;

public class DocumentSummary {

    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String contentHash;
    private int textLength;
    private LocalDateTime updatedAt;

    /** 仅按 id 查详情时返回 */
    private String extractedText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public int getTextLength() { return textLength; }
    public void setTextLength(int textLength) { this.textLength = textLength; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
}
