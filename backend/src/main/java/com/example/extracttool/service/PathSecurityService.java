package com.example.extracttool.service;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件路径/上传的安全校验:
 *  1. 路径必须为绝对路径, 且位于配置的允许根目录下(基于 canonical 路径, 防 ../ 与符号链接逃逸)
 *  2. 文件大小不超过上限
 *  3. 文件扩展名必须在白名单内
 *  4. 通过 Apache Tika 嗅探实际 MIME 类型, 与扩展名必须一致(防伪扩展名)
 * 任意一项不通过抛 IllegalArgumentException, 由 GlobalExceptionHandler 返回 400。
 */
@Service
public class PathSecurityService {

    /** 扩展名 -> 允许的(嗅探出的)MIME 类型集合. 包含同类型的 Tika 变体与容器格式(zip 兜底). */
    private static final Map<String, Set<String>> EXT_TO_MIMES = buildExtToMimes();

    private final Tika tika;
    private final String[] allowedBasePaths;
    private final Set<String> allowedExtensions;
    private final long maxFileSizeBytes;

    public PathSecurityService(
            Tika tika,
            @Value("${app.security.allowed-base-paths:}") String allowedBasePathsStr,
            @Value("${app.security.allowed-extensions:pdf,doc,docx,txt,html,htm,rtf,md,xls,xlsx,ppt,pptx,odt,csv,xml}") String allowedExtStr,
            @Value("${app.security.max-file-size-bytes:104857600}") long maxFileSizeBytes) {
        this.tika = tika;
        this.allowedBasePaths = splitCsv(allowedBasePathsStr);
        this.allowedExtensions = Arrays.stream(splitCsv(allowedExtStr))
                .map(s -> s.toLowerCase().trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    private static String[] splitCsv(String s) {
        if (s == null) return new String[0];
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toArray(String[]::new);
    }

    private static Map<String, Set<String>> buildExtToMimes() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("pdf",  new HashSet<>(Arrays.asList("application/pdf")));
        m.put("doc",  new HashSet<>(Arrays.asList("application/msword", "application/x-tika-msoffice")));
        m.put("docx", new HashSet<>(Arrays.asList(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/zip")));
        m.put("txt",  new HashSet<>(Arrays.asList("text/plain")));
        m.put("html", new HashSet<>(Arrays.asList("text/html")));
        m.put("htm",  new HashSet<>(Arrays.asList("text/html")));
        m.put("rtf",  new HashSet<>(Arrays.asList("application/rtf", "text/rtf")));
        m.put("md",   new HashSet<>(Arrays.asList("text/markdown", "text/x-markdown", "text/plain")));
        m.put("xls",  new HashSet<>(Arrays.asList("application/vnd.ms-excel", "application/x-tika-msoffice")));
        m.put("xlsx", new HashSet<>(Arrays.asList(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/zip")));
        m.put("ppt",  new HashSet<>(Arrays.asList("application/vnd.ms-powerpoint")));
        m.put("pptx", new HashSet<>(Arrays.asList(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/zip")));
        m.put("odt",  new HashSet<>(Arrays.asList("application/vnd.oasis.opendocument.text")));
        m.put("csv",  new HashSet<>(Arrays.asList("text/csv", "text/plain")));
        m.put("xml",  new HashSet<>(Arrays.asList("application/xml", "text/xml")));
        return m;
    }

    /** 服务器路径校验: 绝对路径、存在、普通文件、大小、扩展名、MIME 嗅探、canonical 路径在白名单内 */
    public void validateForPath(File file) {
        if (file == null) {
            throw new IllegalArgumentException("文件路径为空");
        }
        if (!file.isAbsolute()) {
            throw new IllegalArgumentException("仅支持绝对路径: " + file.getPath());
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + file.getPath());
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("不是普通文件(可能是目录或设备): " + file.getPath());
        }
        checkSize(file.length(), "文件");
        String ext = checkExtension(file.getName());
        // MIME 嗅探: 从文件头检测实际类型
        String detected;
        try {
            detected = tika.detect(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("MIME 嗅探失败: " + e.getMessage());
        }
        validateMime(ext, detected);
        // 路径白名单(基于 canonical 路径)
        if (allowedBasePaths.length > 0) {
            String canonical = canonicalize(file);
            boolean ok = false;
            for (String base : allowedBasePaths) {
                String bc = canonicalize(new File(base));
                if (canonical.equals(bc) || canonical.startsWith(bc + File.separator)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw new IllegalArgumentException(
                        "文件路径不在允许范围内: " + canonical
                                + " (允许根目录: " + String.join(", ", allowedBasePaths) + ")");
            }
        }
    }

    /**
     * 上传校验: 大小、扩展名、MIME 嗅探(读取字节). 返回字节数组避免重复 getBytes()。
     */
    public byte[] validateForUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        checkSize(file.getSize(), "上传文件");
        String name = file.getOriginalFilename();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("上传文件名为空");
        }
        String ext = checkExtension(name);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("读取上传文件失败: " + e.getMessage());
        }
        String detected = tika.detect(bytes);
        validateMime(ext, detected);
        return bytes;
    }

    private void validateMime(String ext, String detectedRaw) {
        // 去掉 charset 等参数, 仅比较主类型
        String detected = detectedRaw == null ? "" : detectedRaw.split(";")[0].trim().toLowerCase();
        Set<String> allowed = EXT_TO_MIMES.get(ext);
        if (allowed == null) {
            throw new IllegalArgumentException("不支持的扩展名: ." + ext);
        }
        if (!allowed.contains(detected)) {
            throw new IllegalArgumentException(
                    "文件类型与扩展名不符: 声明=." + ext + ", 实际=" + detectedRaw
                            + " (期望: " + String.join(", ", allowed) + ")");
        }
    }

    /** @return 校验通过的扩展名(小写, 不含点) */
    private String checkExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new IllegalArgumentException("文件缺少扩展名: " + filename);
        }
        String ext = filename.substring(dot + 1).toLowerCase();
        if (!allowedExtensions.contains(ext)) {
            throw new IllegalArgumentException(
                    "不支持的文件类型: ." + ext + "，允许: " + String.join(", .", allowedExtensions));
        }
        return ext;
    }

    private void checkSize(long size, String label) {
        if (size > maxFileSizeBytes) {
            throw new IllegalArgumentException(String.format(
                    "%s过大 (%d bytes), 超过限制 %d bytes (%d MB)",
                    label, size, maxFileSizeBytes, maxFileSizeBytes / 1024 / 1024));
        }
    }

    private static String canonicalize(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("无法解析路径: " + f.getPath() + " (" + e.getMessage() + ")");
        }
    }
}
