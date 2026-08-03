package com.example.extracttool.dto;

import javax.validation.constraints.NotBlank;

public class MenuRequest {
    private Long parentId;
    @NotBlank(message = "菜单名称不能为空") private String name;
    private String path;
    private String icon;
    private String permission;
    private String type = "MENU";
    private Integer sortOrder = 0;
    private Boolean visible = true;
    private String status = "ENABLED";
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
