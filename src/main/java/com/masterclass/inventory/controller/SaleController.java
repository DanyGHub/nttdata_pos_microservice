package com.masterclass.inventory.controller;

import com.masterclass.inventory.dto.SaleDtos.SaleRequest;
import com.masterclass.inventory.dto.SaleDtos.SaleResponse;
import com.masterclass.inventory.service.SaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
@Tag(name = "sale-controller", description = "Endpoints for POS multi-item sales processing and sales history")
public class SaleController {

    private final SaleService service;

    @PostMapping
    @Operation(summary = "Process sale", description = "Processes a multi-item sale, validates stock, decrements inventory, creates SALE movements, and saves sale details")
    public ResponseEntity<SaleResponse> create(@Valid @RequestBody SaleRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @GetMapping
    @Operation(summary = "List sales", description = "Retrieves all completed POS sales transactions")
    public List<SaleResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get sale by ID", description = "Retrieves a single completed sale transaction including its line item details")
    public SaleResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
