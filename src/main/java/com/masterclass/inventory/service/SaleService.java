package com.masterclass.inventory.service;

import com.masterclass.inventory.dto.SaleDtos.SaleItemRequest;
import com.masterclass.inventory.dto.SaleDtos.SaleRequest;
import com.masterclass.inventory.dto.SaleDtos.SaleResponse;
import com.masterclass.inventory.entity.InventoryMovement;
import com.masterclass.inventory.entity.MovementType;
import com.masterclass.inventory.entity.Product;
import com.masterclass.inventory.entity.ProductStatus;
import com.masterclass.inventory.entity.Sale;
import com.masterclass.inventory.entity.SaleDetail;
import com.masterclass.inventory.exception.BusinessException;
import com.masterclass.inventory.exception.NotFoundException;
import com.masterclass.inventory.mapper.SaleMapper;
import com.masterclass.inventory.repository.InventoryMovementRepository;
import com.masterclass.inventory.repository.ProductRepository;
import com.masterclass.inventory.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final ProductRepository products;
    private final SaleRepository sales;
    private final InventoryMovementRepository movements;
    private final SaleMapper mapper;

    @Transactional
    public SaleResponse create(SaleRequest req) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new BusinessException("Sale must contain at least one item");
        }

        Sale sale = Sale.builder()
                .saleDate(OffsetDateTime.now())
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (SaleItemRequest item : req.items()) {
            Product p = products.findById(item.productId())
                    .orElseThrow(() -> new NotFoundException("Product not found with ID: " + item.productId()));

            if (p.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException("Product is not active: " + p.getSku());
            }

            if (p.getCurrentStock() < item.quantity()) {
                throw new BusinessException("Insufficient stock for SKU " + p.getSku()
                        + " (available: " + p.getCurrentStock() + ", requested: " + item.quantity() + ")");
            }

            p.decreaseStock(item.quantity());

            BigDecimal subtotal = p.getUnitPrice().multiply(BigDecimal.valueOf(item.quantity()));

            sale.addDetail(SaleDetail.builder()
                    .product(p)
                    .quantity(item.quantity())
                    .unitPrice(p.getUnitPrice())
                    .subtotal(subtotal)
                    .build());

            total = total.add(subtotal);

            movements.save(InventoryMovement.builder()
                    .product(p)
                    .movementType(MovementType.SALE)
                    .quantity(item.quantity())
                    .date(OffsetDateTime.now())
                    .user("pos-terminal")
                    .build());
        }

        sale.setTotalAmount(total);
        return mapper.toResponse(sales.save(sale));
    }

    @Transactional(readOnly = true)
    public List<SaleResponse> list() {
        return sales.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SaleResponse get(Long id) {
        return mapper.toResponse(sales.findById(id)
                .orElseThrow(() -> new NotFoundException("Sale not found with ID: " + id)));
    }
}
