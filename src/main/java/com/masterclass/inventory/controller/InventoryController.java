package com.masterclass.inventory.controller;

import com.masterclass.inventory.dto.InventoryDtos.AdjustmentCommand;
import com.masterclass.inventory.dto.InventoryDtos.InventoryCommand;
import com.masterclass.inventory.dto.InventoryDtos.MovementResponse;
import com.masterclass.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "inventory-controller", description = "Endpoints for inventory replenishment, adjustments, and movement audit history")
public class InventoryController {

    private final InventoryService service;

    @PostMapping("/replenishment")
    @Operation(summary = "Replenish stock", description = "Increases current product stock and records an audit movement row of type REPLENISHMENT")
    public ResponseEntity<MovementResponse> replenish(@Valid @RequestBody InventoryCommand c) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.replenish(c));
    }

    @PostMapping("/adjustment")
    @Operation(summary = "Adjust inventory", description = "Performs an inventory adjustment (ADJUSTMENT_IN or ADJUSTMENT_OUT) and records a movement row")
    public ResponseEntity<MovementResponse> adjust(@Valid @RequestBody AdjustmentCommand c) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adjust(c));
    }

    @GetMapping("/movements/{productId}")
    @Operation(summary = "Get movement history", description = "Retrieves all historical inventory movements for a given product ordered by date descending")
    public List<MovementResponse> history(@PathVariable Long productId) {
        return service.history(productId);
    }
}
