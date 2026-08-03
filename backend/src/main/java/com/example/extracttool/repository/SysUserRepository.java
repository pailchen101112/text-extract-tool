package com.example.extracttool.repository;

import com.example.extracttool.entity.SysUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    boolean existsByUsername(String username);
    boolean existsByCompanyId(Long companyId);
    boolean existsByPositionId(Long positionId);
    @EntityGraph(attributePaths = {"roles", "roles.menus"})
    Optional<SysUser> findByUsername(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from SysUser u where u.username = :username")
    Optional<SysUser> findForLogin(@Param("username") String username);
    @EntityGraph(attributePaths = {"roles", "roles.menus"})
    List<SysUser> findAllByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = {"roles", "roles.menus"})
    Optional<SysUser> findWithRolesById(Long id);
}
