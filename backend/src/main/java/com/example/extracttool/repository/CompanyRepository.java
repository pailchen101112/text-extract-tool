package com.example.extracttool.repository;

import com.example.extracttool.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByCode(String code);
    Optional<Company> findByCode(String code);
    boolean existsByParentId(Long parentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Company c order by c.id")
    List<Company> findAllForHierarchyUpdate();
    List<Company> findAllByOrderByNameAsc();
}
