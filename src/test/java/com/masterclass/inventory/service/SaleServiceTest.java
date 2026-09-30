package com.masterclass.inventory.service;

import com.masterclass.inventory.dto.SaleDtos.SaleDetailResponse;
import com.masterclass.inventory.dto.SaleDtos.SaleItemRequest;
import com.masterclass.inventory.dto.SaleDtos.SaleRequest;
import com.masterclass.inventory.dto.SaleDtos.SaleResponse;
import com.masterclass.inventory.entity.InventoryMovement;
import com.masterclass.inventory.entity.MovementType;
import com.masterclass.inventory.entity.Product;
import com.masterclass.inventory.entity.ProductStatus;
import com.masterclass.inventory.entity.Sale;
import com.masterclass.inventory.exception.BusinessException;
import com.masterclass.inventory.exception.NotFoundException;
import com.masterclass.inventory.mapper.SaleMapper;
import com.masterclass.inventory.repository.InventoryMovementRepository;
import com.masterclass.inventory.repository.ProductRepository;
import com.masterclass.inventory.repository.SaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private ProductRepository products;

    @Mock
    private SaleRepository sales;

    @Mock
    private InventoryMovementRepository movements;

    @Mock
    private SaleMapper mapper;

    @InjectMocks
    private SaleService service;

    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        product1 = Product.builder()
                .id(1L)
                .sku("APL-001")
                .name("Red Apples")
                .unitPrice(new BigDecimal("2.99"))
                .currentStock(10)
                .reorderLevel(2)
                .status(ProductStatus.ACTIVE)
                .build();

        product2 = Product.builder()
                .id(2L)
                .sku("MLK-001")
                .name("Whole Milk")
                .unitPrice(new BigDecimal("1.79"))
                .currentStock(5)
                .reorderLevel(1)
                .status(ProductStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should post a multi-item sale, calculate subtotals/total, reduce stock, and record movements")
    void create_multiItemSale_success() {
        SaleRequest request = new SaleRequest(List.of(
                new SaleItemRequest(1L, 3), // 3 * 2.99 = 8.97
                new SaleItemRequest(2L, 2)  // 2 * 1.79 = 3.58 -> total = 12.55
        ));

        when(products.findById(1L)).thenReturn(Optional.of(product1));
        when(products.findById(2L)).thenReturn(Optional.of(product2));

        when(sales.save(any(Sale.class))).thenAnswer(inv -> {
            Sale s = inv.getArgument(0);
            s.setId(500L);
            return s;
        });

        SaleResponse expectedResponse = new SaleResponse(
                500L,
                OffsetDateTime.now(),
                new BigDecimal("12.55"),
                List.of(
                        new SaleDetailResponse(1L, "APL-001", "Red Apples", 3, new BigDecimal("2.99"), new BigDecimal("8.97")),
                        new SaleDetailResponse(2L, "MLK-001", "Whole Milk", 2, new BigDecimal("1.79"), new BigDecimal("3.58"))
                )
        );
        when(mapper.toResponse(any(Sale.class))).thenReturn(expectedResponse);

        SaleResponse response = service.create(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.totalAmount()).isEqualTo(new BigDecimal("12.55"));
        assertThat(response.details()).hasSize(2);

        // Verify stock reductions
        assertThat(product1.getCurrentStock()).isEqualTo(7); // 10 - 3
        assertThat(product2.getCurrentStock()).isEqualTo(3); // 5 - 2

        // Verify movements recorded for every item with type SALE
        ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movements, times(2)).save(movementCaptor.capture());
        List<InventoryMovement> recordedMovements = movementCaptor.getAllValues();

        assertThat(recordedMovements.get(0).getProduct().getId()).isEqualTo(1L);
        assertThat(recordedMovements.get(0).getMovementType()).isEqualTo(MovementType.SALE);
        assertThat(recordedMovements.get(0).getQuantity()).isEqualTo(3);

        assertThat(recordedMovements.get(1).getProduct().getId()).isEqualTo(2L);
        assertThat(recordedMovements.get(1).getMovementType()).isEqualTo(MovementType.SALE);
        assertThat(recordedMovements.get(1).getQuantity()).isEqualTo(2);

        // Verify sale total calculation persisted
        ArgumentCaptor<Sale> saleCaptor = ArgumentCaptor.forClass(Sale.class);
        verify(sales).save(saleCaptor.capture());
        Sale savedSale = saleCaptor.getValue();
        assertThat(savedSale.getTotalAmount()).isEqualTo(new BigDecimal("12.55"));
        assertThat(savedSale.getDetails()).hasSize(2);
    }

    @Test
    @DisplayName("Should reject sale when a product is inactive")
    void create_inactiveProduct_throwsBusinessException() {
        product1.setStatus(ProductStatus.INACTIVE);
        when(products.findById(1L)).thenReturn(Optional.of(product1));

        SaleRequest request = new SaleRequest(List.of(new SaleItemRequest(1L, 1)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Product is not active");

        verify(sales, never()).save(any());
        verify(movements, never()).save(any());
        assertThat(product1.getCurrentStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should reject sale when stock is insufficient")
    void create_insufficientStock_throwsBusinessException() {
        when(products.findById(1L)).thenReturn(Optional.of(product1));

        // Request 15 apples when only 10 available
        SaleRequest request = new SaleRequest(List.of(new SaleItemRequest(1L, 15)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");

        verify(sales, never()).save(any());
        verify(movements, never()).save(any());
        assertThat(product1.getCurrentStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should reject sale when product does not exist")
    void create_productNotFound_throwsNotFoundException() {
        when(products.findById(999L)).thenReturn(Optional.empty());

        SaleRequest request = new SaleRequest(List.of(new SaleItemRequest(999L, 1)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(sales, never()).save(any());
        verify(movements, never()).save(any());
    }

    @Test
    @DisplayName("Should reject sale when basket items list is empty")
    void create_emptyBasket_throwsBusinessException() {
        SaleRequest request = new SaleRequest(List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Sale must contain at least one item");

        verify(sales, never()).save(any());
    }

    @Test
    @DisplayName("Should list all completed sales")
    void list_returnsAllSales() {
        Sale s = Sale.builder().id(1L).totalAmount(new BigDecimal("10.00")).build();
        when(sales.findAll()).thenReturn(List.of(s));
        when(mapper.toResponse(s)).thenReturn(new SaleResponse(1L, OffsetDateTime.now(), new BigDecimal("10.00"), List.of()));

        List<SaleResponse> result = service.list();

        assertThat(result).hasSize(1);
        verify(sales).findAll();
    }

    @Test
    @DisplayName("Should get sale by ID successfully")
    void get_saleExists_returnsResponse() {
        Sale s = Sale.builder().id(1L).totalAmount(new BigDecimal("10.00")).build();
        when(sales.findById(1L)).thenReturn(Optional.of(s));
        when(mapper.toResponse(s)).thenReturn(new SaleResponse(1L, OffsetDateTime.now(), new BigDecimal("10.00"), List.of()));

        SaleResponse result = service.get(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should throw NotFoundException when sale ID is not found")
    void get_saleNotFound_throwsNotFoundException() {
        when(sales.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Sale not found");
    }
}
