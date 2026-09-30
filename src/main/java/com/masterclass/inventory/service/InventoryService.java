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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository products;
    private final InventoryMovementRepository movements;

    @Transactional
    public MovementResponse replenish(InventoryCommand c) {
        return move(c.productId(), MovementType.REPLENISHMENT, c.quantity(), c.user());
    }

    @Transactional
    public MovementResponse adjust(AdjustmentCommand c) {
        if (c.type() != MovementType.ADJUSTMENT_IN && c.type() != MovementType.ADJUSTMENT_OUT) {
            throw new BusinessException("Invalid adjustment type: " + c.type() + ". Only ADJUSTMENT_IN and ADJUSTMENT_OUT are permitted.");
        }
        return move(c.productId(), c.type(), c.quantity(), c.user());
    }

    @Transactional
    public MovementResponse move(Long pid, MovementType type, int qty, String user) {
        if (qty <= 0) {
            throw new BusinessException("Movement quantity must be greater than zero");
        }

        Product p = products.findById(pid)
                .orElseThrow(() -> new NotFoundException("Product not found with ID: " + pid));

        if (p.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException("Cannot mutate inventory for inactive product: " + p.getSku());
        }

        if (type == MovementType.REPLENISHMENT || type == MovementType.ADJUSTMENT_IN) {
            p.increaseStock(qty);
        } else if (type == MovementType.ADJUSTMENT_OUT || type == MovementType.SALE) {
            if (p.getCurrentStock() < qty) {
                throw new BusinessException("Insufficient stock for product " + p.getSku()
                        + " (available: " + p.getCurrentStock() + ", requested: " + qty + ")");
            }
            p.decreaseStock(qty);
        } else {
            throw new BusinessException("Unsupported movement type: " + type);
        }

        InventoryMovement m = movements.save(InventoryMovement.builder()
                .product(p)
                .movementType(type)
                .quantity(qty)
                .date(OffsetDateTime.now())
                .user(user)
                .build());

        return new MovementResponse(
                m.getId(),
                p.getId(),
                p.getSku(),
                m.getMovementType(),
                m.getQuantity(),
                m.getDate(),
                m.getUser()
        );
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> history(Long productId) {
        if (!products.existsById(productId)) {
            throw new NotFoundException("Product not found with ID: " + productId);
        }
        return movements.findByProductIdOrderByDateDesc(productId).stream()
                .map(m -> new MovementResponse(
                        m.getId(),
                        m.getProduct().getId(),
                        m.getProduct().getSku(),
                        m.getMovementType(),
                        m.getQuantity(),
                        m.getDate(),
                        m.getUser()
                )).toList();
    }
}
