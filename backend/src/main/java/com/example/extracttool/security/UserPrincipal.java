package com.example.extracttool.security;

import com.example.extracttool.entity.SysMenu;
import com.example.extracttool.entity.SysRole;
import com.example.extracttool.entity.SysUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class UserPrincipal implements UserDetails {
    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final boolean mustChangePassword;
    private final Set<GrantedAuthority> authorities;

    private UserPrincipal(Long id, String username, String password, boolean enabled,
                          boolean accountNonLocked, boolean mustChangePassword,
                          Set<GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.mustChangePassword = mustChangePassword;
        this.authorities = authorities;
    }

    public static UserPrincipal from(SysUser user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (SysRole role : user.getRoles()) {
            if (!"ENABLED".equals(role.getStatus())) continue;
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
            if ("SUPER_ADMIN".equals(role.getCode())) authorities.add(new SimpleGrantedAuthority("*"));
            for (SysMenu menu : role.getMenus()) {
                if ("ENABLED".equals(menu.getStatus()) && menu.getPermission() != null && !menu.getPermission().trim().isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority(menu.getPermission().trim()));
                }
            }
        }
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(java.time.LocalDateTime.now());
        return new UserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash(),
                "ENABLED".equals(user.getStatus()), !locked, Boolean.TRUE.equals(user.getMustChangePassword()), authorities);
    }

    public Long getId() { return id; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
