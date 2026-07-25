package com.example.extracttool.controller;

import com.example.extracttool.dto.SearchRequest;
import com.example.extracttool.dto.SearchResponse;
import com.example.extracttool.service.SearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** 判断多个文本(needles)是否在目标文本中 */
    @PostMapping("/match")
    public SearchResponse match(@RequestBody SearchRequest request) {
        return searchService.match(request);
    }
}
