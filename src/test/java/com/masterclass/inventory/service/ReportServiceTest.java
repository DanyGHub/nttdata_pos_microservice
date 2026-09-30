package com.masterclass.inventory.service;

import com.masterclass.inventory.dto.InventoryDtos.MovementResponse;
import com.masterclass.inventory.dto.ReportDtos.DailySalesReport;
import com.masterclass.inventory.dto.ReportDtos.ProductInventoryReport;
import com.masterclass.inventory.entity.Category;
import com.masterclass.inventory.entity.MovementType;
import com.masterclass.inventory.entity.Product;
import com.masterclass.inventory.entity.ProductStatus;
import com.masterclass.inventory.entity.Sale;
import com.masterclass.inventory.repository.ProductRepository;
import com.masterclass.inventory.repository.SaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ProductRepository products;

    @Mock
    private SaleRepository sales;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ReportService service;

    private Category testCategory;
    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L)
                .name("Produce")
                .description("Fresh fruits")
                .build();

        product1 = Product.builder()
                .id(1L)
                .sku("APL-001")
                .name("Red Apples")
                .category(testCategory)
                .unitPrice(new BigDecimal("2.50"))
                .currentStock(10)
                .reorderLevel(15) // stock (10) <= reorder (15) -> LOW STOCK
                .status(ProductStatus.ACTIVE)
                .build();

        product2 = Product.builder()
                .id(2L)
                .sku("ORA-001")
                .name("Oranges")
                .category(testCategory)
                .unitPrice(new BigDecimal("3.00"))
                .currentStock(50)
                .reorderLevel(10) // stock (50) > reorder (10) -> NORMAL STOCK
                .status(ProductStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should generate inventory report with correct stock valuation")
    void inventory_calculatesStockValueCorrectly() {
        when(products.findAllWithCategory()).thenReturn(List.of(product1, product2));

        List<ProductInventoryReport> report = service.inventory();

        assertThat(report).hasSize(2);

        // Product 1: 10 * 2.50 = 25.00
        ProductInventoryReport r1 = report.get(0);
        assertThat(r1.sku()).isEqualTo("APL-001");
        assertThat(r1.category()).isEqualTo("Produce");
        assertThat(r1.stock()).isEqualTo(10);
        assertThat(r1.stockValue()).isEqualByComparingTo(new BigDecimal("25.00"));

        // Product 2: 50 * 3.00 = 150.00
        ProductInventoryReport r2 = report.get(1);
        assertThat(r2.sku()).isEqualTo("ORA-001");
        assertThat(r2.stock()).isEqualTo(50);
        assertThat(r2.stockValue()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("Should generate low stock report returning only products at or below reorder level")
    void lowStock_returnsOnlyActiveProductsBelowOrAtReorderLevel() {
        when(products.findLowStock(ProductStatus.ACTIVE)).thenReturn(List.of(product1));

        List<ProductInventoryReport> lowStockReport = service.lowStock();

        assertThat(lowStockReport).hasSize(1);
        assertThat(lowStockReport.get(0).sku()).isEqualTo("APL-001");
        assertThat(lowStockReport.get(0).stock()).isEqualTo(10);
        assertThat(lowStockReport.get(0).reorderLevel()).isEqualTo(15);
    }

    @Test
    @DisplayName("Should generate daily sales report summing transactions and total sales amount")
    void daily_calculatesTransactionsAndTotalAmount() {
        LocalDate queryDate = LocalDate.of(2026, 9, 29);

        Sale s1 = Sale.builder().id(1L).totalAmount(new BigDecimal("14.95")).build();
        Sale s2 = Sale.builder().id(2L).totalAmount(new BigDecimal("25.05")).build();

        when(sales.findSalesBetween(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(s1, s2));

        DailySalesReport report = service.daily(queryDate);

        assertThat(report.date()).isEqualTo(queryDate);
        assertThat(report.transactions()).isEqualTo(2);
        assertThat(report.totalSales()).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    @DisplayName("Should return zero sales and zero transactions when no sales exist for the day")
    void daily_noSales_returnsZeroTotals() {
        LocalDate queryDate = LocalDate.of(2026, 9, 29);

        when(sales.findSalesBetween(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        DailySalesReport report = service.daily(queryDate);

        assertThat(report.date()).isEqualTo(queryDate);
        assertThat(report.transactions()).isEqualTo(0);
        assertThat(report.totalSales()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should delegate movement history retrieval to InventoryService")
    void movements_delegatesToInventoryService() {
        MovementResponse m = new MovementResponse(
                1L, 1L, "APL-001", MovementType.REPLENISHMENT, 20, OffsetDateTime.now(), "admin"
        );
        when(inventoryService.history(1L)).thenReturn(List.of(m));

        List<MovementResponse> result = service.movements(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sku()).isEqualTo("APL-001");
        verify(inventoryService).history(1L);
    }
}
