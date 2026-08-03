package com.example.extracttool.repository;

import com.example.extracttool.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {
    List<SysMenu> findAllByOrderBySortOrderAscIdAsc();
    List<SysMenu> findByStatusOrderBySortOrderAscIdAsc(String status);
}
