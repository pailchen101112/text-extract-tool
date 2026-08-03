package com.example.extracttool.controller;

import com.example.extracttool.dto.ExtractRequest;
import com.example.extracttool.dto.ExtractResponse;
import com.example.extracttool.service.ExtractService;
import com.example.extracttool.service.ClientIpService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/extract")
public class ExtractController {

    private final ExtractService extractService;
    private final ClientIpService clientIpService;

    public ExtractController(ExtractService extractService, ClientIpService clientIpService) {
        this.extractService = extractService;
        this.clientIpService = clientIpService;
    }

    /** 按服务器本地文件路径提取文本 */
    @PostMapping("/path")
    @PreAuthorize("@authz.has('attachment:extract')")
    public ExtractResponse extractByPath(@RequestBody ExtractRequest request,
                                         HttpServletRequest httpRequest) {
        return extractService.extractByPath(request, clientIpService.resolve(httpRequest));
    }

    /** 按上传文件流提取文本 */
    @PostMapping("/upload")
    @PreAuthorize("@authz.has('attachment:extract')")
    public ExtractResponse extractByUpload(@RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "useCache", defaultValue = "true") boolean useCache,
                                           HttpServletRequest httpRequest) {
        return extractService.extractByUpload(file, useCache, clientIpService.resolve(httpRequest));
    }
}
