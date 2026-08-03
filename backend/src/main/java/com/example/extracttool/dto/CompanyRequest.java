package com.example.extracttool.dto;

import javax.validation.constraints.NotBlank;

public class CompanyRequest {
    @NotBlank(message = "公司编码不能为空") private String code;
    @NotBlank(message = "公司名称不能为空") private String name;
    private Long parentId;
    private String status = "ENABLED";
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
