package com.masterclass.inventory.controller;

import com.masterclass.inventory.dto.InventoryDtos.MovementResponse;
import com.masterclass.inventory.dto.ReportDtos.DailySalesReport;
import com.masterclass.inventory.dto.ReportDtos.ProductInventoryReport;
import com.masterclass.inventory.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "report-controller", description = "Endpoints for operational reporting, inventory valuation, daily sales, and movements")
public class ReportController {

    private final ReportService service;

    @GetMapping("/inventory")
    @Operation(summary = "Inventory value report", description = "Retrieves current stock levels and total valuation for all products in catalog")
    public List<ProductInventoryReport> inventory() {
        return service.inventory();
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Low stock alert report", description = "Retrieves active products whose current stock is less than or equal to their reorder threshold")
    public List<ProductInventoryReport> lowStock() {
        return service.lowStock();
    }

    @GetMapping("/daily-sales")
    @Operation(summary = "Daily sales report", description = "Summarizes completed sales transactions and total sales amount for a given calendar date")
    public DailySalesReport daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.daily(date != null ? date : LocalDate.now());
    }

    @GetMapping("/movements/{productId}")
    @Operation(summary = "Product movement history", description = "Retrieves all audit inventory movements for a specific product")
    public List<MovementResponse> movements(@PathVariable Long productId) {
        return service.movements(productId);
    }
}
