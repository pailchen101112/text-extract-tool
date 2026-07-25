package com.example.extracttool.controller;

import com.example.extracttool.dto.ExtractRequest;
import com.example.extracttool.dto.ExtractResponse;
import com.example.extracttool.service.ExtractService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/extract")
public class ExtractController {

    private final ExtractService extractService;

    public ExtractController(ExtractService extractService) {
        this.extractService = extractService;
    }

    /** 按服务器本地文件路径提取文本 */
    @PostMapping("/path")
    public ExtractResponse extractByPath(@RequestBody ExtractRequest request,
                                         HttpServletRequest httpRequest) {
        return extractService.extractByPath(request, getClientIp(httpRequest));
    }

    /** 按上传文件流提取文本 */
    @PostMapping("/upload")
    public ExtractResponse extractByUpload(@RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "useCache", defaultValue = "true") boolean useCache,
                                           HttpServletRequest httpRequest) {
        return extractService.extractByUpload(file, useCache, getClientIp(httpRequest));
    }

    /** 优先 X-Forwarded-For, 否则取 remoteAddr */
    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
