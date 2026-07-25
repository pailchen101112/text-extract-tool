package com.example.extracttool.service;

import com.example.extracttool.dto.ExtractRequest;
import com.example.extracttool.dto.ExtractResponse;
import com.example.extracttool.entity.ExtractedDocument;
import com.example.extracttool.repository.ExtractedDocumentRepository;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * 文本提取与缓存服务。
 * 缓存策略: 按文件名查询, 用 content_hash(SHA-256) 校验内容;
 *   - 命中且 hash 一致 -> 直接返回缓存(cacheHit=true)
 *   - 命中但 hash 不同 -> 重新提取并更新
 *   - 未命中 -> 提取并新增
 * 每次操作通过 AuditLogService 记录审计日志(成功/失败)。
 */
@Service
public class ExtractService {

    private final ExtractedDocumentRepository repo;
    private final TikaService tikaService;
    private final PathSecurityService pathSecurityService;
    private final AuditLogService auditLogService;

    public ExtractService(ExtractedDocumentRepository repo,
                          TikaService tikaService,
                          PathSecurityService pathSecurityService,
                          AuditLogService auditLogService) {
        this.repo = repo;
        this.tikaService = tikaService;
        this.pathSecurityService = pathSecurityService;
        this.auditLogService = auditLogService;
    }

    /** 按服务器本地文件路径提取 */
    public ExtractResponse extractByPath(ExtractRequest request, String clientIp) {
        String filePath = request == null ? null : request.getFilePath();
        String fileName = "(unknown)";
        String hash = null;
        Long size = null;
        try {
            if (filePath == null || filePath.trim().isEmpty()) {
                throw new IllegalArgumentException("filePath 不能为空");
            }
            File file = new File(filePath);
            fileName = file.getName();
            // 安全校验: 绝对路径/存在/普通文件/大小/扩展名/MIME/路径白名单
            pathSecurityService.validateForPath(file);
            hash = sha256(file);
            size = file.length();
            boolean useCache = request.getUseCache() == null || request.getUseCache();

            if (useCache) {
                Optional<ExtractedDocument> cached = repo.findByFileName(fileName);
                if (cached.isPresent() && hash.equals(cached.get().getContentHash())) {
                    ExtractResponse resp = toResponse(cached.get(), true, false,
                            len(cached.get().getExtractedText()));
                    auditLogService.logExtract("path", fileName, hash, size, clientIp, true, null);
                    return resp;
                }
            }

            TikaService.ExtractResult result;
            try (InputStream is = new FileInputStream(file)) {
                result = tikaService.extract(is, fileName);
            } catch (IOException e) {
                throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
            } catch (TikaException e) {
                throw new RuntimeException("文本提取失败: " + e.getMessage(), e);
            }

            ExtractedDocument doc = upsert(fileName, hash, file.getAbsolutePath(),
                    file.length(), result.getFileType(), result.getText());
            ExtractResponse resp = toResponse(doc, false, result.isTruncated(), result.getOriginalLength());
            auditLogService.logExtract("path", fileName, hash, size, clientIp, true, null);
            return resp;
        } catch (RuntimeException e) {
            auditLogService.logExtract("path", fileName, hash, size, clientIp, false, e.getMessage());
            throw e;
        }
    }

    /** 按上传文件流提取 */
    public ExtractResponse extractByUpload(MultipartFile file, boolean useCache, String clientIp) {
        String rawName = file == null ? null : file.getOriginalFilename();
        String fileName = (rawName == null || rawName.trim().isEmpty()) ? "(unknown)" : rawName;
        String hash = null;
        Long size = null;
        try {
            // 安全校验: 大小/扩展名/MIME; 同时返回字节避免重复 getBytes()
            byte[] bytes = pathSecurityService.validateForUpload(file);
            // 校验通过后, rawName 保证非空
            fileName = rawName;
            size = (long) bytes.length;
            hash = sha256(bytes);

            if (useCache) {
                Optional<ExtractedDocument> cached = repo.findByFileName(fileName);
                if (cached.isPresent() && hash.equals(cached.get().getContentHash())) {
                    ExtractResponse resp = toResponse(cached.get(), true, false,
                            len(cached.get().getExtractedText()));
                    auditLogService.logExtract("upload", fileName, hash, size, clientIp, true, null);
                    return resp;
                }
            }

            TikaService.ExtractResult result;
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                result = tikaService.extract(is, fileName);
            } catch (IOException e) {
                throw new RuntimeException("读取文件流失败: " + e.getMessage(), e);
            } catch (TikaException e) {
                throw new RuntimeException("文本提取失败: " + e.getMessage(), e);
            }

            ExtractedDocument doc = upsert(fileName, hash, null,
                    (long) bytes.length, result.getFileType(), result.getText());
            ExtractResponse resp = toResponse(doc, false, result.isTruncated(), result.getOriginalLength());
            auditLogService.logExtract("upload", fileName, hash, size, clientIp, true, null);
            return resp;
        } catch (RuntimeException e) {
            auditLogService.logExtract("upload", fileName, hash, size, clientIp, false, e.getMessage());
            throw e;
        }
    }

    /** 按文件名 upsert 缓存 */
    private ExtractedDocument upsert(String fileName, String hash, String filePath,
                                     long fileSize, String fileType, String text) {
        ExtractedDocument doc = repo.findByFileName(fileName)
                .orElseGet(ExtractedDocument::new);
        doc.setFileName(fileName);
        doc.setContentHash(hash);
        if (filePath != null) {
            doc.setFilePath(filePath);
        }
        doc.setFileSize(fileSize);
        doc.setFileType(fileType);
        doc.setExtractedText(text);
        return repo.save(doc);
    }

    private ExtractResponse toResponse(ExtractedDocument doc, boolean cacheHit,
                                       boolean truncated, int originalLength) {
        ExtractResponse resp = new ExtractResponse();
        resp.setFileName(doc.getFileName());
        resp.setFileType(doc.getFileType());
        resp.setFileSize(doc.getFileSize());
        resp.setContentHash(doc.getContentHash());
        resp.setCacheHit(cacheHit);
        resp.setTruncated(truncated);
        resp.setTextLength(originalLength);
        resp.setExtractedText(doc.getExtractedText());
        return resp;
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    // ---- SHA-256 ----

    private static String sha256(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return sha256(fis);
        } catch (IOException e) {
            throw new RuntimeException("计算文件哈希失败: " + e.getMessage(), e);
        }
    }

    private static String sha256(byte[] bytes) {
        return sha256(new ByteArrayInputStream(bytes));
    }

    private static String sha256(InputStream is) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("计算哈希失败: " + e.getMessage(), e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
