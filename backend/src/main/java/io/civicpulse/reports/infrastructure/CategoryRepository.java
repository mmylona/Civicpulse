package io.civicpulse.reports.infrastructure;

import io.civicpulse.reports.domain.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findByActiveTrueOrderBySortOrderAsc();

    boolean existsByCodeAndActiveTrue(String code);
}
