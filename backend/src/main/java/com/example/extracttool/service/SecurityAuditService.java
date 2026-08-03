package com.example.extracttool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class SecurityAuditService {
    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");
    private final ClientIpService clientIpService;

    public SecurityAuditService(ClientIpService clientIpService) {
        this.clientIpService = clientIpService;
    }

    public void record(String event, String username, String clientIp, String result, String detail) {
        AUDIT.info("event={} username={} clientIp={} result={} detail={}", clean(event), clean(username),
                clean(clientIp), clean(result), clean(detail));
    }

    public void recordAdmin(String event, String detail) {
        String username = SecurityContextHolder.getContext().getAuthentication() == null ? "-"
                : SecurityContextHolder.getContext().getAuthentication().getName();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String ip = attrs == null ? "-" : clientIpService.resolve(attrs.getRequest());
        record(event, username, ip, "SUCCESS", detail);
    }

    private String clean(String value) {
        return value == null ? "-" : value.replace('\n', '_').replace('\r', '_');
    }
}
