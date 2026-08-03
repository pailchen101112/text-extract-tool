package com.example.extracttool.service;

import com.example.extracttool.dto.ChangePasswordRequest;
import com.example.extracttool.dto.LoginRequest;
import com.example.extracttool.entity.SysMenu;
import com.example.extracttool.entity.SysRole;
import com.example.extracttool.entity.SysUser;
import com.example.extracttool.repository.CompanyRepository;
import com.example.extracttool.repository.PositionRepository;
import com.example.extracttool.repository.SysMenuRepository;
import com.example.extracttool.repository.SysUserRepository;
import com.example.extracttool.security.JwtService;
import com.example.extracttool.security.LoginFailureException;
import com.example.extracttool.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AuthService {
    private final SysUserRepository users;
    private final SysMenuRepository menus;
    private final CompanyRepository companies;
    private final PositionRepository positions;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicy;
    private final JwtService jwtService;
    private final SecurityAuditService audit;
    private final int maxFailedAttempts;
    private final int lockMinutes;
    private final int passwordMaxAgeDays;

    public AuthService(SysUserRepository users, SysMenuRepository menus, CompanyRepository companies,
                       PositionRepository positions, PasswordEncoder passwordEncoder,
                       PasswordPolicyService passwordPolicy, JwtService jwtService, SecurityAuditService audit,
                       @Value("${app.auth.max-failed-attempts:5}") int maxFailedAttempts,
                       @Value("${app.auth.lock-minutes:30}") int lockMinutes,
                       @Value("${app.auth.password-max-age-days:90}") int passwordMaxAgeDays) {
        this.users = users; this.menus = menus; this.companies = companies; this.positions = positions;
        this.passwordEncoder = passwordEncoder; this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService; this.audit = audit; this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes; this.passwordMaxAgeDays = passwordMaxAgeDays;
    }

    @Transactional(noRollbackFor = LoginFailureException.class)
    public Map<String, Object> login(LoginRequest request, String clientIp) {
        String username = request.getUsername().trim();
        Optional<SysUser> found = users.findByUsername(username);
        if (!found.isPresent()) {
            passwordEncoder.matches(request.getPassword(), "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxoOpK7r8VnQyJmHWVFN8cXJx9K");
            audit.record("LOGIN", username, clientIp, "DENIED", "bad_credentials");
            throw new LoginFailureException("账号或密码错误");
        }
        SysUser user = found.get();
        LocalDateTime now = LocalDateTime.now();
        if (!"ENABLED".equals(user.getStatus())) {
            audit.record("LOGIN", username, clientIp, "DENIED", "disabled");
            throw new LoginFailureException("账号已停用");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            audit.record("LOGIN", username, clientIp, "DENIED", "locked");
            throw new LoginFailureException("账号已锁定，请稍后重试");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maxFailedAttempts) user.setLockedUntil(now.plusMinutes(lockMinutes));
            users.save(user);
            audit.record("LOGIN", username, clientIp, "DENIED", "bad_credentials_attempt_" + attempts);
            throw new LoginFailureException(attempts >= maxFailedAttempts ? "账号已锁定，请稍后重试" : "账号或密码错误");
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        if (user.getPasswordUpdatedAt() == null || user.getPasswordUpdatedAt().isBefore(now.minusDays(passwordMaxAgeDays))) {
            user.setMustChangePassword(true);
        }
        users.save(user);
        UserPrincipal principal = UserPrincipal.from(user);
        audit.record("LOGIN", username, clientIp, "SUCCESS", "-");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", jwtService.createToken(principal));
        result.put("expiresIn", jwtService.getExpirationSeconds());
        result.put("user", profile(user));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> currentProfile() { return profile(currentUser()); }

    @Transactional
    public void changePassword(ChangePasswordRequest request, String clientIp) {
        SysUser user = currentUser();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            audit.record("PASSWORD_CHANGE", user.getUsername(), clientIp, "DENIED", "old_password_mismatch");
            throw new IllegalArgumentException("原密码错误");
        }
        passwordPolicy.validate(request.getNewPassword(), user.getUsername());
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        audit.record("PASSWORD_CHANGE", user.getUsername(), clientIp, "SUCCESS", "-");
    }

    private SysUser currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private Map<String, Object> profile(SysUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("displayName", user.getDisplayName());
        result.put("companyId", user.getCompanyId());
        result.put("companyName", user.getCompanyId() == null ? null : companies.findById(user.getCompanyId()).map(c -> c.getName()).orElse(null));
        result.put("positionId", user.getPositionId());
        result.put("positionName", user.getPositionId() == null ? null : positions.findById(user.getPositionId()).map(p -> p.getName()).orElse(null));
        result.put("mustChangePassword", user.getMustChangePassword());
        List<String> roleCodes = new ArrayList<>();
        boolean superAdmin = false;
        Set<Long> allowedMenuIds = new LinkedHashSet<>();
        for (SysRole role : user.getRoles()) {
            if (!"ENABLED".equals(role.getStatus())) continue;
            roleCodes.add(role.getCode());
            if ("SUPER_ADMIN".equals(role.getCode())) superAdmin = true;
            for (SysMenu menu : role.getMenus()) if ("ENABLED".equals(menu.getStatus())) allowedMenuIds.add(menu.getId());
        }
        List<SysMenu> allMenus = menus.findByStatusOrderBySortOrderAscIdAsc("ENABLED");
        if (superAdmin) for (SysMenu menu : allMenus) allowedMenuIds.add(menu.getId());
        boolean changed;
        do {
            changed = false;
            for (SysMenu menu : allMenus) {
                if (allowedMenuIds.contains(menu.getId()) && menu.getParentId() != null && allowedMenuIds.add(menu.getParentId())) changed = true;
            }
        } while (changed);
        List<Map<String, Object>> menuViews = new ArrayList<>();
        Set<String> permissions = new LinkedHashSet<>();
        for (SysMenu menu : allMenus) {
            if (!allowedMenuIds.contains(menu.getId())) continue;
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", menu.getId()); view.put("parentId", menu.getParentId()); view.put("name", menu.getName());
            view.put("path", menu.getPath()); view.put("icon", menu.getIcon()); view.put("permission", menu.getPermission());
            view.put("type", menu.getType()); view.put("sortOrder", menu.getSortOrder()); view.put("visible", menu.getVisible());
            menuViews.add(view);
            if (menu.getPermission() != null && !menu.getPermission().trim().isEmpty()) permissions.add(menu.getPermission());
        }
        if (superAdmin) permissions.add("*");
        result.put("roles", roleCodes);
        result.put("permissions", permissions);
        result.put("menus", menuViews);
        return result;
    }
}
