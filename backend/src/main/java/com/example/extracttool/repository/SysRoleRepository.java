package com.example.extracttool.repository;

import com.example.extracttool.entity.SysRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SysRoleRepository extends JpaRepository<SysRole, Long> {
    boolean existsByCode(String code);
    Optional<SysRole> findByCode(String code);
    @EntityGraph(attributePaths = "menus")
    List<SysRole> findAllByOrderByNameAsc();
    @EntityGraph(attributePaths = "menus")
    Optional<SysRole> findWithMenusById(Long id);
}
