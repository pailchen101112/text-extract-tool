package com.example.extracttool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 操作审计日志. 通过名为 "audit" 的 logger 输出(由 logback-spring.xml 路由到 logs/audit.log 与控制台)。
 * 日志格式: key=value, 便于 grep 与解析。
 */
@Service
public class AuditLogService {

    private static final Logger LOG = LoggerFactory.getLogger("audit");

    /**
     * 记录一次文本提取操作(成功或失败)。
     */
    public void logExtract(String source, String fileName, String contentHash, Long fileSize,
                           String clientIp, boolean success, String error) {
        StringBuilder sb = new StringBuilder("AUDIT ");
        sb.append("source=").append(source == null ? "-" : source);
        sb.append(" fileName=").append(fileName == null ? "-" : sanitize(fileName));
        sb.append(" contentHash=").append(contentHash == null ? "-" : contentHash);
        sb.append(" fileSize=").append(fileSize == null ? "-" : fileSize);
        sb.append(" clientIp=").append(clientIp == null ? "-" : clientIp);
        sb.append(" result=").append(success ? "ok" : "error");
        if (error != null) {
            sb.append(" error=\"").append(sanitize(error)).append("\"");
        }
        LOG.info(sb.toString());
    }

    /** 去掉换行与引号, 防止日志注入与格式错乱 */
    private static String sanitize(String s) {
        return s.replace('\n', ' ').replace('\r', ' ').replace('"', '\'');
    }
}
