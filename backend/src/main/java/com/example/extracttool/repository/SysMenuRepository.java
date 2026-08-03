package com.example.extracttool.repository;

import com.example.extracttool.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import javax.persistence.LockModeType;
import java.util.List;

public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {
    List<SysMenu> findAllByOrderBySortOrderAscIdAsc();
    List<SysMenu> findByStatusOrderBySortOrderAscIdAsc(String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from SysMenu m order by m.id")
    List<SysMenu> findAllForHierarchyUpdate();
}
