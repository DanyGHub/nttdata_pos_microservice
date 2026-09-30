package com.masterclass.inventory.controller;

import com.masterclass.inventory.dto.ProductDtos.ProductRequest;
import com.masterclass.inventory.dto.ProductDtos.ProductResponse;
import com.masterclass.inventory.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "product-controller", description = "Endpoints for managing products, search, updates, and deactivation")
public class ProductController {

    private final ProductService service;

    @PostMapping
    @Operation(summary = "Create product", description = "Creates a new product with validation on SKU, category, price, and stock")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @GetMapping
    @Operation(summary = "List or search products", description = "Retrieves all products or filters by SKU/name search query term")
    public List<ProductResponse> list(@RequestParam(required = false) String q) {
        return service.list(q);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Retrieves a single product by its primary key identifier")
    public ProductResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product", description = "Updates editable attributes of an existing product")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate product", description = "Performs logical deactivation (sets status to INACTIVE)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
