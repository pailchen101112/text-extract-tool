package com.example.extracttool.dto;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class RoleRequest {
    @NotBlank(message = "角色编码不能为空") private String code;
    @NotBlank(message = "角色名称不能为空") private String name;
    private String description;
    private String status = "ENABLED";
    private List<Long> menuIds = new ArrayList<>();
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<Long> getMenuIds() { return menuIds; }
    public void setMenuIds(List<Long> menuIds) { this.menuIds = menuIds; }
}
