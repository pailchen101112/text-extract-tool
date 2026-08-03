package com.example.extracttool.service;

import com.example.extracttool.dto.*;
import com.example.extracttool.entity.*;
import com.example.extracttool.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {
    private final CompanyRepository companies;
    private final PositionRepository positions;
    private final SysMenuRepository menus;
    private final SysRoleRepository roles;
    private final SysUserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicyService passwordPolicy;
    private final SecurityAuditService audit;

    public AdminService(CompanyRepository companies, PositionRepository positions, SysMenuRepository menus,
                        SysRoleRepository roles, SysUserRepository users, PasswordEncoder encoder,
                        PasswordPolicyService passwordPolicy, SecurityAuditService audit) {
        this.companies = companies; this.positions = positions; this.menus = menus; this.roles = roles;
        this.users = users; this.encoder = encoder; this.passwordPolicy = passwordPolicy; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Company> listCompanies() { return companies.findAllByOrderByNameAsc(); }

    @Transactional
    public Company saveCompany(Long id, CompanyRequest request) {
        companies.findAllForHierarchyUpdate();
        Company entity = id == null ? new Company() : company(id);
        companies.findByCode(request.getCode().trim()).filter(found -> !found.getId().equals(id)).ifPresent(found -> { throw new IllegalArgumentException("公司编码已存在"); });
        validateCompanyParent(id, request.getParentId());
        entity.setCode(request.getCode().trim().toUpperCase()); entity.setName(request.getName().trim());
        entity.setParentId(request.getParentId()); entity.setStatus(status(request.getStatus()));
        Company saved = companies.save(entity);
        audit.recordAdmin(id == null ? "COMPANY_CREATE" : "COMPANY_UPDATE", "id=" + saved.getId());
        return saved;
    }

    @Transactional
    public void deleteCompany(Long id) {
        if (companies.existsByParentId(id) || positions.existsByCompanyId(id) || users.existsByCompanyId(id)) {
            throw new IllegalArgumentException("公司仍有关联下级公司、岗位或用户，不能删除");
        }
        companies.delete(company(id)); audit.recordAdmin("COMPANY_DELETE", "id=" + id);
    }

    @Transactional(readOnly = true)
    public List<Position> listPositions() { return positions.findAllByOrderByNameAsc(); }

    @Transactional
    public Position savePosition(Long id, PositionRequest request) {
        company(request.getCompanyId());
        Position entity = id == null ? new Position() : position(id);
        positions.findByCode(request.getCode().trim()).filter(found -> !found.getId().equals(id)).ifPresent(found -> { throw new IllegalArgumentException("岗位编码已存在"); });
        entity.setCompanyId(request.getCompanyId()); entity.setCode(request.getCode().trim().toUpperCase());
        entity.setName(request.getName().trim()); entity.setStatus(status(request.getStatus()));
        Position saved = positions.save(entity);
        audit.recordAdmin(id == null ? "POSITION_CREATE" : "POSITION_UPDATE", "id=" + saved.getId());
        return saved;
    }

    @Transactional
    public void deletePosition(Long id) {
        if (users.existsByPositionId(id)) throw new IllegalArgumentException("岗位仍有关联用户，不能删除");
        positions.delete(position(id)); audit.recordAdmin("POSITION_DELETE", "id=" + id);
    }

    @Transactional(readOnly = true)
    public List<SysMenu> listMenus() { return menus.findAllByOrderBySortOrderAscIdAsc(); }

    @Transactional
    public SysMenu saveMenu(Long id, MenuRequest request) {
        menus.findAllForHierarchyUpdate();
        SysMenu entity = id == null ? new SysMenu() : menu(id);
        if (id != null) requirePermissionInScope(entity.getPermission());
        String requestedPermission = trimToNull(request.getPermission());
        requirePermissionInScope(requestedPermission);
        validateMenuParent(id, request.getParentId());
        entity.setParentId(request.getParentId()); entity.setName(request.getName().trim()); entity.setPath(trimToNull(request.getPath()));
        entity.setIcon(trimToNull(request.getIcon())); entity.setPermission(requestedPermission);
        entity.setType(request.getType() == null ? "MENU" : request.getType().trim().toUpperCase());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setVisible(request.getVisible() == null || request.getVisible()); entity.setStatus(status(request.getStatus()));
        SysMenu saved = menus.save(entity);
        audit.recordAdmin(id == null ? "MENU_CREATE" : "MENU_UPDATE", "id=" + saved.getId());
        return saved;
    }

    @Transactional
    public void deleteMenu(Long id) {
        for (SysMenu child : menus.findAllByOrderBySortOrderAscIdAsc()) {
            if (id.equals(child.getParentId())) throw new IllegalArgumentException("请先删除该菜单的子项");
        }
        SysMenu target = menu(id);
        requirePermissionInScope(target.getPermission());
        for (SysRole role : roles.findAllByOrderByNameAsc()) {
            if (role.getMenus().remove(target)) roles.save(role);
        }
        menus.delete(target); audit.recordAdmin("MENU_DELETE", "id=" + id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRoles() {
        return roles.findAllByOrderByNameAsc().stream().map(this::roleView).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> saveRole(Long id, RoleRequest request) {
        SysRole entity = id == null ? new SysRole() : role(id);
        if (id != null) requireRoleInScope(entity);
        String requestedCode = request.getCode().trim().toUpperCase();
        if ("SUPER_ADMIN".equals(requestedCode) && !isSuperAdmin()) {
            throw new IllegalArgumentException("仅超级管理员可维护超级管理员角色");
        }
        if (id != null && "SUPER_ADMIN".equals(entity.getCode()) && !"SUPER_ADMIN".equals(request.getCode().trim().toUpperCase())) {
            throw new IllegalArgumentException("超级管理员角色编码不可修改");
        }
        roles.findByCode(request.getCode().trim().toUpperCase()).filter(found -> !found.getId().equals(id)).ifPresent(found -> { throw new IllegalArgumentException("角色编码已存在"); });
        entity.setCode(requestedCode); entity.setName(request.getName().trim());
        entity.setDescription(trimToNull(request.getDescription())); entity.setStatus(status(request.getStatus()));
        List<Long> menuIds = request.getMenuIds() == null ? Collections.emptyList() : request.getMenuIds();
        List<SysMenu> selected = menus.findAllById(menuIds);
        if (selected.size() != new HashSet<>(menuIds).size()) throw new IllegalArgumentException("包含不存在的菜单");
        selected.forEach(menu -> requirePermissionInScope(menu.getPermission()));
        entity.setMenus(new LinkedHashSet<>(selected));
        SysRole saved = roles.save(entity);
        audit.recordAdmin(id == null ? "ROLE_CREATE" : "ROLE_UPDATE", "id=" + saved.getId());
        return roleView(saved);
    }

    @Transactional
    public void deleteRole(Long id) {
        SysRole target = role(id);
        if ("SUPER_ADMIN".equals(target.getCode())) throw new IllegalArgumentException("超级管理员角色不能删除");
        requireRoleInScope(target);
        for (SysUser user : users.findAllByOrderByCreatedAtDesc()) {
            if (user.getRoles().remove(target)) users.save(user);
        }
        roles.delete(target); audit.recordAdmin("ROLE_DELETE", "id=" + id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUsers() {
        return users.findAllByOrderByCreatedAtDesc().stream().map(this::userView).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> saveUser(Long id, UserRequest request) {
        SysUser entity = id == null ? new SysUser() : user(id);
        if (id != null) requireUserInScope(entity);
        String username = request.getUsername().trim();
        if (id == null && users.existsByUsername(username)) throw new IllegalArgumentException("用户名已存在");
        if (id != null && !entity.getUsername().equals(username) && users.existsByUsername(username)) throw new IllegalArgumentException("用户名已存在");
        if (request.getCompanyId() != null) company(request.getCompanyId());
        if (request.getPositionId() != null) {
            Position position = position(request.getPositionId());
            if (!Objects.equals(position.getCompanyId(), request.getCompanyId())) throw new IllegalArgumentException("岗位不属于所选公司");
        }
        if (id == null && (request.getPassword() == null || request.getPassword().isEmpty())) throw new IllegalArgumentException("新建用户必须设置初始密码");
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            passwordPolicy.validate(request.getPassword(), username);
            entity.setPasswordHash(encoder.encode(request.getPassword())); entity.setPasswordUpdatedAt(LocalDateTime.now());
            entity.setMustChangePassword(request.getMustChangePassword() == null || request.getMustChangePassword());
        }
        List<Long> roleIds = request.getRoleIds() == null ? Collections.emptyList() : request.getRoleIds();
        List<SysRole> selectedRoles = roles.findAllById(roleIds);
        if (selectedRoles.size() != new HashSet<>(roleIds).size()) throw new IllegalArgumentException("包含不存在的角色");
        selectedRoles.forEach(this::requireRoleInScope);
        entity.setUsername(username); entity.setDisplayName(request.getDisplayName().trim()); entity.setEmail(trimToNull(request.getEmail()));
        entity.setPhone(trimToNull(request.getPhone())); entity.setCompanyId(request.getCompanyId()); entity.setPositionId(request.getPositionId());
        entity.setStatus(status(request.getStatus())); entity.setRoles(new LinkedHashSet<>(selectedRoles));
        if (request.getMustChangePassword() != null) entity.setMustChangePassword(request.getMustChangePassword());
        SysUser saved = users.save(entity);
        audit.recordAdmin(id == null ? "USER_CREATE" : "USER_UPDATE", "id=" + saved.getId());
        return userView(saved);
    }

    @Transactional
    public void unlockUser(Long id) {
        SysUser entity = user(id); requireUserInScope(entity);
        entity.setFailedLoginAttempts(0); entity.setLockedUntil(null); users.save(entity);
        audit.recordAdmin("USER_UNLOCK", "id=" + id);
    }

    @Transactional
    public void deleteUser(Long id) {
        SysUser target = user(id);
        requireUserInScope(target);
        String current = SecurityContextHolder.getContext().getAuthentication().getName();
        if (target.getUsername().equals(current)) throw new IllegalArgumentException("不能删除当前登录账号");
        users.delete(target); audit.recordAdmin("USER_DELETE", "id=" + id);
    }

    private Map<String, Object> roleView(SysRole role) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", role.getId()); view.put("code", role.getCode()); view.put("name", role.getName());
        view.put("description", role.getDescription()); view.put("status", role.getStatus());
        view.put("menuIds", role.getMenus().stream().map(SysMenu::getId).collect(Collectors.toList()));
        view.put("createdAt", role.getCreatedAt()); return view;
    }

    private Map<String, Object> userView(SysUser user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId()); view.put("username", user.getUsername()); view.put("displayName", user.getDisplayName());
        view.put("email", user.getEmail()); view.put("phone", user.getPhone()); view.put("companyId", user.getCompanyId());
        view.put("positionId", user.getPositionId()); view.put("status", user.getStatus());
        view.put("failedLoginAttempts", user.getFailedLoginAttempts()); view.put("lockedUntil", user.getLockedUntil());
        view.put("lastLoginAt", user.getLastLoginAt()); view.put("mustChangePassword", user.getMustChangePassword());
        view.put("roleIds", user.getRoles().stream().map(SysRole::getId).collect(Collectors.toList()));
        view.put("roleNames", user.getRoles().stream().map(SysRole::getName).collect(Collectors.toList()));
        view.put("createdAt", user.getCreatedAt()); return view;
    }

    private Company company(Long id) { return companies.findById(id).orElseThrow(() -> new IllegalArgumentException("公司不存在")); }
    private Position position(Long id) { return positions.findById(id).orElseThrow(() -> new IllegalArgumentException("岗位不存在")); }
    private SysMenu menu(Long id) { return menus.findById(id).orElseThrow(() -> new IllegalArgumentException("菜单不存在")); }
    private SysRole role(Long id) { return roles.findWithMenusById(id).orElseThrow(() -> new IllegalArgumentException("角色不存在")); }
    private SysUser user(Long id) { return users.findWithRolesById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在")); }
    private void validateCompanyParent(Long id, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if ((id != null && id.equals(cursor)) || !visited.add(cursor)) throw new IllegalArgumentException("公司层级不能形成循环");
            cursor = company(cursor).getParentId();
        }
    }
    private void validateMenuParent(Long id, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if ((id != null && id.equals(cursor)) || !visited.add(cursor)) throw new IllegalArgumentException("菜单层级不能形成循环");
            cursor = menu(cursor).getParentId();
        }
    }
    private boolean isSuperAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "*".equals(authority.getAuthority()));
    }
    private Set<String> currentPermissions() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> !authority.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }
    private void requirePermissionInScope(String permission) {
        if (permission == null || permission.isEmpty() || isSuperAdmin()) return;
        if ("*".equals(permission) || !currentPermissions().contains(permission)) {
            throw new IllegalArgumentException("不能维护超出当前账号授权范围的权限");
        }
    }
    private void requireRoleInScope(SysRole role) {
        if (isSuperAdmin()) return;
        if ("SUPER_ADMIN".equals(role.getCode())) throw new IllegalArgumentException("不能维护超级管理员角色");
        for (SysMenu assignedMenu : role.getMenus()) requirePermissionInScope(assignedMenu.getPermission());
    }
    private void requireUserInScope(SysUser user) {
        if (isSuperAdmin()) return;
        for (SysRole assignedRole : user.getRoles()) requireRoleInScope(assignedRole);
    }
    private String status(String value) { return "DISABLED".equalsIgnoreCase(value) ? "DISABLED" : "ENABLED"; }
    private String trimToNull(String value) { if (value == null || value.trim().isEmpty()) return null; return value.trim(); }
}
