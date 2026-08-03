package com.example.extracttool.repository;

import com.example.extracttool.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByCode(String code);
    Optional<Company> findByCode(String code);
    List<Company> findAllByOrderByNameAsc();
}
