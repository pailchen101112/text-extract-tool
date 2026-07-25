package com.example.extracttool.dto;

import java.util.List;

public class SearchRequest {

    /** 从缓存按文件名取目标文本(二选一) */
    private String fileName;

    /** 内联目标文本(二选一) */
    private String haystack;

    /** 待判断是否存在的多个文本 */
    private List<String> needles;

    /** 是否区分大小写, 默认 false */
    private Boolean caseSensitive = false;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getHaystack() { return haystack; }
    public void setHaystack(String haystack) { this.haystack = haystack; }

    public List<String> getNeedles() { return needles; }
    public void setNeedles(List<String> needles) { this.needles = needles; }

    public Boolean getCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(Boolean caseSensitive) { this.caseSensitive = caseSensitive; }
}
