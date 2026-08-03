package com.example.extracttool.repository;

import com.example.extracttool.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {
    boolean existsByCode(String code);
    Optional<Position> findByCode(String code);
    boolean existsByCompanyId(Long companyId);
    List<Position> findAllByOrderByNameAsc();
}
