package com.example.extracttool.dto;

public class ExtractRequest {

    /** 服务器本地文件的绝对路径 */
    private String filePath;

    /** 是否使用缓存(命中且 hash 一致则直接返回), 默认 true */
    private Boolean useCache = true;

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Boolean getUseCache() { return useCache; }
    public void setUseCache(Boolean useCache) { this.useCache = useCache; }
}
