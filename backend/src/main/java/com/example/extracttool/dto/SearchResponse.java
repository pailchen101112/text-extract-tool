package com.example.extracttool.dto;

import java.util.List;

public class SearchResponse {

    /** 目标文本来源: cache(按文件名命中缓存) 或 inline(内联文本) */
    private String source;
    private int haystackLength;
    private List<NeedleResult> results;

    public SearchResponse() {}

    public SearchResponse(String source, int haystackLength, List<NeedleResult> results) {
        this.source = source;
        this.haystackLength = haystackLength;
        this.results = results;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public int getHaystackLength() { return haystackLength; }
    public void setHaystackLength(int haystackLength) { this.haystackLength = haystackLength; }

    public List<NeedleResult> getResults() { return results; }
    public void setResults(List<NeedleResult> results) { this.results = results; }
}
