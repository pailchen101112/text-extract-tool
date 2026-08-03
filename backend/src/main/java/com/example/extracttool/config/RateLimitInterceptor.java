package com.example.extracttool.config;

import com.example.extracttool.service.RateLimitService;
import com.example.extracttool.service.ClientIpService;
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
    private final ClientIpService clientIpService;

    public RateLimitInterceptor(RateLimitService rateLimitService, ClientIpService clientIpService) {
        this.rateLimitService = rateLimitService;
        this.clientIpService = clientIpService;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        String ip = clientIpService.resolve(req);
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

}
