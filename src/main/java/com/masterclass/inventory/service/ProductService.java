package com.masterclass.inventory.service;

import com.masterclass.inventory.dto.ProductDtos.*;
import com.masterclass.inventory.entity.*;
import com.masterclass.inventory.exception.*;
import com.masterclass.inventory.mapper.ProductMapper;
import com.masterclass.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductMapper mapper;

    @Transactional
    public ProductResponse create(ProductRequest r) {
        if (products.existsBySku(r.sku())) {
            throw new BusinessException("SKU already exists: " + r.sku());
        }
        Category c = categories.findById(r.categoryId())
                .orElseThrow(() -> new NotFoundException("Category not found: " + r.categoryId()));
        Product p = mapper.toEntity(r);
        p.setCategory(c);
        return mapper.toResponse(products.save(p));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(String term) {
        var result = (term == null || term.isBlank()) ? products.findAll() : products.search(term);
        return result.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return mapper.toResponse(find(id));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest r) {
        Product p = find(id);
        products.findBySku(r.sku())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException("SKU already in use by another product: " + r.sku());
                });
        Category c = categories.findById(r.categoryId())
                .orElseThrow(() -> new NotFoundException("Category not found: " + r.categoryId()));
        p.setSku(r.sku());
        p.setName(r.name());
        p.setDescription(r.description());
        p.setCategory(c);
        p.setUnitPrice(r.unitPrice());
        p.setCurrentStock(r.currentStock());
        p.setReorderLevel(r.reorderLevel());
        p.setStatus(r.status());
        return mapper.toResponse(p);
    }

    @Transactional
    public void delete(Long id) {
        Product p = find(id);
        p.setStatus(ProductStatus.INACTIVE);
    }

    Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }
}
