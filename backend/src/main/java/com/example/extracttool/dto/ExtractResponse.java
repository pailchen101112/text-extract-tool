package com.example.extracttool.dto;

public class ExtractResponse {

    private String fileName;
    private String fileType;
    private Long fileSize;
    private String contentHash;
    private boolean cacheHit;
    private int textLength;
    private boolean truncated;
    private String extractedText;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public boolean isCacheHit() { return cacheHit; }
    public void setCacheHit(boolean cacheHit) { this.cacheHit = cacheHit; }

    public int getTextLength() { return textLength; }
    public void setTextLength(int textLength) { this.textLength = textLength; }

    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
}
