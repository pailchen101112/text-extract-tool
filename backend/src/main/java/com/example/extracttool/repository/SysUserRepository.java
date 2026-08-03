package com.example.extracttool.repository;

import com.example.extracttool.entity.SysUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    boolean existsByUsername(String username);
    boolean existsByCompanyId(Long companyId);
    boolean existsByPositionId(Long positionId);
    @EntityGraph(attributePaths = {"roles", "roles.menus"})
    Optional<SysUser> findByUsername(String username);
    @EntityGraph(attributePaths = {"roles", "roles.menus"})
    List<SysUser> findAllByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = {"roles", "roles.menus"})
    Optional<SysUser> findWithRolesById(Long id);
}
