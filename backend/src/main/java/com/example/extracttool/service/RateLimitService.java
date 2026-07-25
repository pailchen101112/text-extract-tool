package com.example.extracttool.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的按 IP 固定窗口(每分钟)限流。内存实现, 进程内生效, 适合单实例工具。
 * 当 map 超过阈值时进行机会性清理, 防止长期运行内存泄漏。
 */
@Service
public class RateLimitService {

    private static final long MINUTE_MS = 60_000L;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final boolean enabled;
    private final int limit;
    private final ConcurrentHashMap<String, Window> map = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.requests-per-minute:60}") int limit) {
        this.enabled = enabled;
        this.limit = limit;
    }

    /**
     * 尝试为该 key(通常为 IP)获取一次配额。返回是否允许, 以及不允许时的 Retry-After 秒数。
     */
    public Result tryAcquire(String key) {
        if (!enabled) {
            return Result.ALLOWED;
        }
        if (map.size() > CLEANUP_THRESHOLD) {
            cleanup();
        }
        long minute = System.currentTimeMillis() / MINUTE_MS;
        Window w = map.computeIfAbsent(key, k -> new Window(minute, 0));
        boolean allowed;
        int retryAfter = 0;
        synchronized (w) {
            if (w.minute != minute) {
                w.minute = minute;
                w.count = 0;
            }
            w.count++;
            if (w.count > limit) {
                allowed = false;
                long secInMinute = (System.currentTimeMillis() / 1000) % 60;
                retryAfter = (int) (60 - secInMinute);
                if (retryAfter <= 0) {
                    retryAfter = 1;
                }
            } else {
                allowed = true;
            }
        }
        return allowed ? Result.ALLOWED : new Result(false, retryAfter);
    }

    private void cleanup() {
        long cur = System.currentTimeMillis() / MINUTE_MS;
        Iterator<Map.Entry<String, Window>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().minute < cur - 1) {
                it.remove();
            }
        }
    }

    private static class Window {
        long minute;
        int count;
        Window(long m, int c) { this.minute = m; this.count = c; }
    }

    public static final class Result {
        public static final Result ALLOWED = new Result(true, 0);
        public final boolean allowed;
        public final int retryAfter;
        Result(boolean allowed, int retryAfter) {
            this.allowed = allowed;
            this.retryAfter = retryAfter;
        }
    }
}
