package com.masterclass.inventory.service;

import com.masterclass.inventory.dto.InventoryDtos.AdjustmentCommand;
import com.masterclass.inventory.dto.InventoryDtos.InventoryCommand;
import com.masterclass.inventory.dto.InventoryDtos.MovementResponse;
import com.masterclass.inventory.entity.InventoryMovement;
import com.masterclass.inventory.entity.MovementType;
import com.masterclass.inventory.entity.Product;
import com.masterclass.inventory.entity.ProductStatus;
import com.masterclass.inventory.exception.BusinessException;
import com.masterclass.inventory.exception.NotFoundException;
import com.masterclass.inventory.repository.InventoryMovementRepository;
import com.masterclass.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class InventoryServiceTest {

    @Mock
    private ProductRepository products;

    @Mock
    private InventoryMovementRepository movements;

    @InjectMocks
    private InventoryService service;

    private Product activeProduct;

    @BeforeEach
    void setUp() {
        activeProduct = Product.builder()
                .id(1L)
                .sku("APL-001")
                .name("Red Apples")
                .unitPrice(new BigDecimal("2.99"))
                .currentStock(50)
                .reorderLevel(10)
                .status(ProductStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should replenish stock and record a REPLENISHMENT movement")
    void replenish_success() {
        InventoryCommand cmd = new InventoryCommand(1L, 25, "admin");

        when(products.findById(1L)).thenReturn(Optional.of(activeProduct));
        when(movements.save(any(InventoryMovement.class))).thenAnswer(inv -> {
            InventoryMovement m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });

        MovementResponse response = service.replenish(cmd);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.movementType()).isEqualTo(MovementType.REPLENISHMENT);
        assertThat(response.quantity()).isEqualTo(25);
        assertThat(response.user()).isEqualTo("admin");

        // Stock should have mutated from 50 to 75
        assertThat(activeProduct.getCurrentStock()).isEqualTo(75);
        verify(movements).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("Should increase stock when adjustment type is ADJUSTMENT_IN")
    void adjust_in_success() {
        AdjustmentCommand cmd = new AdjustmentCommand(1L, 10, MovementType.ADJUSTMENT_IN, "manager");

        when(products.findById(1L)).thenReturn(Optional.of(activeProduct));
        when(movements.save(any(InventoryMovement.class))).thenAnswer(inv -> {
            InventoryMovement m = inv.getArgument(0);
            m.setId(101L);
            return m;
        });

        MovementResponse response = service.adjust(cmd);

        assertThat(response.movementType()).isEqualTo(MovementType.ADJUSTMENT_IN);
        assertThat(activeProduct.getCurrentStock()).isEqualTo(60);
        verify(movements).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("Should decrease stock when adjustment type is ADJUSTMENT_OUT and stock is sufficient")
    void adjust_out_success() {
        AdjustmentCommand cmd = new AdjustmentCommand(1L, 20, MovementType.ADJUSTMENT_OUT, "manager");

        when(products.findById(1L)).thenReturn(Optional.of(activeProduct));
        when(movements.save(any(InventoryMovement.class))).thenAnswer(inv -> {
            InventoryMovement m = inv.getArgument(0);
            m.setId(102L);
            return m;
        });

        MovementResponse response = service.adjust(cmd);

        assertThat(response.movementType()).isEqualTo(MovementType.ADJUSTMENT_OUT);
        assertThat(activeProduct.getCurrentStock()).isEqualTo(30);
        verify(movements).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("Should block invalid adjustment type (e.g. REPLENISHMENT or SALE passed to adjust)")
    void adjust_invalidType_throwsBusinessException() {
        AdjustmentCommand cmd = new AdjustmentCommand(1L, 10, MovementType.REPLENISHMENT, "user1");

        assertThatThrownBy(() -> service.adjust(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid adjustment type");

        verify(products, never()).findById(any());
        verify(movements, never()).save(any());
    }

    @Test
    @DisplayName("Should block adjustment out beyond available stock")
    void adjust_outBeyondStock_throwsBusinessException() {
        // Current stock is 50, trying to adjust out 60
        AdjustmentCommand cmd = new AdjustmentCommand(1L, 60, MovementType.ADJUSTMENT_OUT, "manager");

        when(products.findById(1L)).thenReturn(Optional.of(activeProduct));

        assertThatThrownBy(() -> service.adjust(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");

        // Stock should remain unchanged at 50
        assertThat(activeProduct.getCurrentStock()).isEqualTo(50);
        verify(movements, never()).save(any());
    }

    @Test
    @DisplayName("Should throw NotFoundException when product does not exist")
    void move_productNotFound_throwsException() {
        InventoryCommand cmd = new InventoryCommand(999L, 10, "admin");

        when(products.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replenish(cmd))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(movements, never()).save(any());
    }

    @Test
    @DisplayName("Should throw BusinessException when trying to mutate inactive product")
    void move_inactiveProduct_throwsException() {
        activeProduct.setStatus(ProductStatus.INACTIVE);
        InventoryCommand cmd = new InventoryCommand(1L, 10, "admin");

        when(products.findById(1L)).thenReturn(Optional.of(activeProduct));

        assertThatThrownBy(() -> service.replenish(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot mutate inventory for inactive product");

        verify(movements, never()).save(any());
    }

    @Test
    @DisplayName("Should return movement history ordered by date descending")
    void history_success() {
        InventoryMovement m1 = InventoryMovement.builder()
                .id(1L)
                .product(activeProduct)
                .movementType(MovementType.REPLENISHMENT)
                .quantity(20)
                .date(OffsetDateTime.now())
                .user("admin")
                .build();

        when(products.existsById(1L)).thenReturn(true);
        when(movements.findByProductIdOrderByDateDesc(1L)).thenReturn(List.of(m1));

        List<MovementResponse> history = service.history(1L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).sku()).isEqualTo("APL-001");
        assertThat(history.get(0).movementType()).isEqualTo(MovementType.REPLENISHMENT);
    }

    @Test
    @DisplayName("Should throw NotFoundException when fetching history for non-existent product")
    void history_productNotFound_throwsException() {
        when(products.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.history(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(movements, never()).findByProductIdOrderByDateDesc(any());
    }
}
