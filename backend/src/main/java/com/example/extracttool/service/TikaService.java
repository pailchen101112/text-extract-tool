package com.example.extracttool.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 基于 Apache Tika 的文本提取. 支持 pdf / word(doc,docx) / txt / html / rtf 等。
 * Tika 实例线程安全, 单例复用。
 */
@Service
public class TikaService {

    private final Tika tika;
    private final int maxTextLength;

    public TikaService(Tika tika,
                       @Value("${app.extract.max-text-length:1000000}") int maxTextLength) {
        this.tika = tika;
        this.maxTextLength = maxTextLength;
    }

    /** 提取结果 */
    public static class ExtractResult {
        private final String text;
        private final String fileType;
        private final int originalLength;
        private final boolean truncated;

        public ExtractResult(String text, String fileType, int originalLength, boolean truncated) {
            this.text = text;
            this.fileType = fileType;
            this.originalLength = originalLength;
            this.truncated = truncated;
        }

        public String getText() { return text; }
        public String getFileType() { return fileType; }
        public int getOriginalLength() { return originalLength; }
        public boolean isTruncated() { return truncated; }
    }

    /**
     * 提取文本。使用无 writeLimit 的 parseToString 读取全文, 再按配置上限截断。
     * 文件类型从 Tika 写入 metadata 的 Content-Type 获取, 并结合文件名兜底。
     */
    public ExtractResult extract(InputStream stream, String fileName) throws IOException, TikaException {
        Metadata metadata = new Metadata();
        if (fileName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        String full = tika.parseToString(stream, metadata);
        String mimeType = metadata.get(HttpHeaders.CONTENT_TYPE);
        String fileType = mapType(mimeType, fileName);

        int originalLength = full.length();
        boolean truncated = originalLength > maxTextLength;
        String text = truncated ? full.substring(0, maxTextLength) : full;
        return new ExtractResult(text, fileType, originalLength, truncated);
    }

    private String mapType(String mimeType, String fileName) {
        if (mimeType != null) {
            String m = mimeType.toLowerCase();
            if (m.contains("pdf")) return "pdf";
            if (m.contains("wordprocessingml")) return "docx";
            if (m.contains("msword")) return "doc";
            if (m.contains("excel")) return "xls/xlsx";
            if (m.contains("powerpoint")) return "ppt/pptx";
            if (m.contains("text/plain")) return "txt";
            if (m.contains("html")) return "html";
            if (m.contains("rtf")) return "rtf";
        }
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                return fileName.substring(dot + 1).toLowerCase();
            }
        }
        return mimeType == null ? "unknown" : mimeType;
    }
}
