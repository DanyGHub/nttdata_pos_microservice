package com.masterclass.inventory.service;

import com.masterclass.inventory.dto.InventoryDtos.MovementResponse;
import com.masterclass.inventory.dto.ReportDtos.DailySalesReport;
import com.masterclass.inventory.dto.ReportDtos.ProductInventoryReport;
import com.masterclass.inventory.entity.Product;
import com.masterclass.inventory.entity.ProductStatus;
import com.masterclass.inventory.repository.ProductRepository;
import com.masterclass.inventory.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ProductRepository products;
    private final SaleRepository sales;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public List<ProductInventoryReport> inventory() {
        return products.findAllWithCategory().stream()
                .map(this::toProductInventoryReport)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductInventoryReport> lowStock() {
        return products.findLowStock(ProductStatus.ACTIVE).stream()
                .map(this::toProductInventoryReport)
                .toList();
    }

    @Transactional(readOnly = true)
    public DailySalesReport daily(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        var from = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        var to = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        var rows = sales.findSalesBetween(from, to);
        var total = rows.stream()
                .map(s -> s.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DailySalesReport(date, rows.size(), total);
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> movements(Long productId) {
        return inventoryService.history(productId);
    }

    private ProductInventoryReport toProductInventoryReport(Product p) {
        BigDecimal stockValue = p.getUnitPrice().multiply(BigDecimal.valueOf(p.getCurrentStock()));
        return new ProductInventoryReport(
                p.getId(),
                p.getSku(),
                p.getName(),
                p.getCategory() != null ? p.getCategory().getName() : "Unassigned",
                p.getCurrentStock(),
                p.getReorderLevel(),
                stockValue
        );
    }
}
