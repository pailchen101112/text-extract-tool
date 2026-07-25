package com.example.extracttool.service;

import com.example.extracttool.dto.NeedleResult;
import com.example.extracttool.dto.SearchRequest;
import com.example.extracttool.dto.SearchResponse;
import com.example.extracttool.entity.ExtractedDocument;
import com.example.extracttool.repository.ExtractedDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 多文本检索服务: 判断多个文本(needles)是否出现在目标文本(haystack)中。
 * 目标文本可来自缓存(按文件名)或内联传入。
 */
@Service
public class SearchService {

    private final ExtractedDocumentRepository repo;

    public SearchService(ExtractedDocumentRepository repo) {
        this.repo = repo;
    }

    public SearchResponse match(SearchRequest request) {
        if (request.getNeedles() == null || request.getNeedles().isEmpty()) {
            throw new IllegalArgumentException("needles 不能为空");
        }

        String haystack;
        String source;
        if (request.getFileName() != null && !request.getFileName().trim().isEmpty()) {
            ExtractedDocument doc = repo.findByFileName(request.getFileName().trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "缓存中未找到文件: " + request.getFileName()));
            haystack = doc.getExtractedText() == null ? "" : doc.getExtractedText();
            source = "cache";
        } else if (request.getHaystack() != null) {
            haystack = request.getHaystack();
            source = "inline";
        } else {
            throw new IllegalArgumentException("需提供 fileName(从缓存取) 或 haystack(内联文本)");
        }

        boolean caseSensitive = request.getCaseSensitive() != null && request.getCaseSensitive();
        String hay = caseSensitive ? haystack : haystack.toLowerCase();

        List<NeedleResult> results = new ArrayList<>();
        for (String needle : request.getNeedles()) {
            if (needle == null) {
                continue;
            }
            String n = caseSensitive ? needle : needle.toLowerCase();
            int count = n.isEmpty() ? 0 : countOccurrences(hay, n);
            int first = count > 0 ? hay.indexOf(n) : -1;
            results.add(new NeedleResult(needle, count > 0, count, first));
        }
        return new SearchResponse(source, haystack.length(), results);
    }

    private static int countOccurrences(String hay, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = hay.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
