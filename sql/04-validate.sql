-- ==============================================================================
-- Hands-on Lab 1: Database Bootstrap - Validation Script
-- Description: Queries seeded data and actively validates relational integrity & constraints
-- ==============================================================================

USE grocery_inventory;
GO

-- 1. Consultar categorias insertadas
PRINT '>>> 1. Validando Categorias Existentes:';
SELECT id, name, description FROM categories;
GO

-- 2. Consultar productos con su categoria asociada
PRINT '>>> 2. Validando Productos y Relacion con Categorias:';
SELECT 
    p.id AS product_id,
    p.sku,
    p.name AS product_name,
    c.name AS category_name,
    p.unit_price,
    p.current_stock,
    p.reorder_level,
    p.status
FROM products p
INNER JOIN categories c ON p.category_id = c.id;
GO

-- 3. Verificacion Activa de Constraints del Modelo (usando TRY...CATCH para validar el bloqueo)
PRINT '>>> 3. Probando Restricciones (Constraints) de forma activa:';
GO

-- A) Prueba de Violacion de Unicidad de SKU (Constraint: uk_products_sku)
BEGIN TRY
    INSERT INTO products(sku, name, category_id, unit_price, current_stock, reorder_level, status)
    VALUES('APL-001', 'Manzana Verde Duplicada', 1, 3.50, 10, 5, 'ACTIVE');
    PRINT 'FALLO: El constraint uk_products_sku debio rechazar el SKU duplicado.';
END TRY
BEGIN CATCH
    PRINT '[EXITO] Constraint uk_products_sku activo. Rechazo SKU duplicado: ' + ERROR_MESSAGE();
END CATCH
GO

-- B) Prueba de Violacion de Stock Negativo (Constraint: ck_products_stock)
BEGIN TRY
    INSERT INTO products(sku, name, category_id, unit_price, current_stock, reorder_level, status)
    VALUES('ERR-001', 'Producto Negativo', 1, 1.00, -5, 5, 'ACTIVE');
    PRINT 'FALLO: El constraint ck_products_stock debio rechazar el stock negativo.';
END TRY
BEGIN CATCH
    PRINT '[EXITO] Constraint ck_products_stock activo. Rechazo stock negativo: ' + ERROR_MESSAGE();
END CATCH
GO

-- C) Prueba de Violacion de Foreign Key (Constraint: fk_products_categories)
BEGIN TRY
    INSERT INTO products(sku, name, category_id, unit_price, current_stock, reorder_level, status)
    VALUES('ERR-002', 'Categoria Inexistente', 9999, 1.00, 10, 5, 'ACTIVE');
    PRINT 'FALLO: El constraint fk_products_categories debio rechazar la categoria inexistente.';
END TRY
BEGIN CATCH
    PRINT '[EXITO] Constraint fk_products_categories activo. Rechazo categoria invalida: ' + ERROR_MESSAGE();
END CATCH
GO

PRINT '>>> Validacion completa: todas las tablas, relaciones y constraints funcionan segun el diseno.';
GO
