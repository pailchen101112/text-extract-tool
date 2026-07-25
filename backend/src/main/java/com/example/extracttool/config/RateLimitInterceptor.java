package com.example.extracttool.config;

import com.example.extracttool.service.RateLimitService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 限流拦截器: 对每个 /api/** 请求按 IP 检查配额, 超限返回 429 + Retry-After + JSON 错误。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        String ip = getClientIp(req);
        RateLimitService.Result r = rateLimitService.tryAcquire(ip);
        if (r.allowed) {
            return true;
        }
        resp.setStatus(429);
        resp.setHeader("Retry-After", String.valueOf(r.retryAfter));
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"error\":\"请求过于频繁, 请 " + r.retryAfter + " 秒后再试\"}");
        return false;
    }

    /** 优先取 X-Forwarded-For(代理后的真实 IP), 否则取 remoteAddr */
    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
