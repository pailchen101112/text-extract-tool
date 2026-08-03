package com.example.extracttool.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class PositionRequest {
    @NotNull(message = "所属公司不能为空") private Long companyId;
    @NotBlank(message = "岗位编码不能为空") private String code;
    @NotBlank(message = "岗位名称不能为空") private String name;
    private String status = "ENABLED";
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
