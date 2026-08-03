package com.example.extracttool.security;

import com.example.extracttool.entity.SysUser;
import com.example.extracttool.repository.SysUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final SysUserRepository userRepository;
    public DatabaseUserDetailsService(SysUserRepository userRepository) { this.userRepository = userRepository; }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
        return UserPrincipal.from(user);
    }
}
