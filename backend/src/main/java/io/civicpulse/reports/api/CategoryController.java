package io.civicpulse.reports.api;

import io.civicpulse.reports.application.CategoryView;
import io.civicpulse.reports.application.ReportService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CategoryController {

    private final ReportService reports;

    CategoryController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/api/v1/categories")
    List<CategoryView> categories() {
        return reports.categories();
    }
}
