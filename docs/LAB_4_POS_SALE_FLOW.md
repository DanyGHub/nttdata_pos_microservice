# Hands-on Lab 4: POS Sale Flow

Modulo: Grocery Inventory POS Microservice Master Class
Objetivo: Implementar y verificar el flujo transaccional de ventas en punto de venta (POS), permitiendo procesar transacciones con multiples productos, validando el estado del catalogo y el inventario disponible, calculando subtotales y totales con precision monetaria, y registrando la salida de existencias en el historial de movimientos de forma atomica.

---

## Criterios de Aceptacion (Acceptance Criteria)

"Sale is created and inventory is reduced for every item."

El sistema debe garantizar:
1. Recepcion de solicitudes de venta compuestas por uno o multiples articulos (multi-item sale).
2. Validacion de que cada producto involucrado se encuentre en estado ACTIVE. Si un producto esta inactivo, se rechaza la venta completa.
3. Validacion de existencias fisicas suficientes para cada linea de la venta. Si cualquier articulo carece de inventario suficiente, se rechaza la venta y se aborta la operacion.
4. Calculo exacto de subtotales por renglon (cantidad * precio unitario) y acumulacion del importe total de la venta utilizando BigDecimal para evitar errores de redondeo.
5. Persistencia atomica bajo una unica transaccion (@Transactional): se inserta la cabecera de la venta (sales), sus renglones (sale_details), se disminuye el stock en el maestro de productos y se crea una fila de auditoria (inventory_movements) con tipo SALE por cada producto vendido.
6. En caso de error en cualquier linea, se ejecuta un Rollback absoluto que impide la alteracion parcial del inventario.

---

## Resolucion de los Puntos del Laboratorio

### Punto 1: Registro de Ventas Multi-articulo (Post a multi-item sale)
El controlador SaleController expone el metodo POST /api/v1/sales recibiendo el payload SaleRequest:

```json
{
  "items": [
    { "productId": 1, "quantity": 3 },
    { "productId": 2, "quantity": 2 }
  ]
}
```

La lista se valida mediante Bean Validation (@NotEmpty y @Valid) para asegurar que no se procesen ventas vacias ni renglones con cantidades menores o iguales a cero.

### Punto 2: Validacion del Estado del Producto (Validate product status)
Durante la iteracion de los articulos en SaleService, se consulta cada producto y se evalua su disponibilidad comercial:

```java
if (p.getStatus() != ProductStatus.ACTIVE) {
    throw new BusinessException("Product is not active: " + p.getSku());
}
```

Si un producto se encuentra en estado INACTIVE, el servicio interrumpe el procesamiento y retorna un codigo HTTP 400 Bad Request, impidiendo que productos discontinuados salgan a la venta.

### Punto 3: Validacion de Stock Disponible (Validate stock)
Antes de comprometer la operacion, se comprueba la cantidad solicitada contra el inventario actual:

```java
if (p.getCurrentStock() < item.quantity()) {
    throw new BusinessException("Insufficient stock for SKU " + p.getSku()
            + " (available: " + p.getCurrentStock() + ", requested: " + item.quantity() + ")");
}
p.decreaseStock(item.quantity());
```

La disminucion del stock se realiza en memoria sobre la entidad gestionada, respetando las invariantes del modelo de dominio.

### Punto 4: Calculo de Subtotales y Total General (Calculate subtotals and total)
Los valores financieros se calculan linea por linea utilizando el tipo BigDecimal:

```java
BigDecimal subtotal = p.getUnitPrice().multiply(BigDecimal.valueOf(item.quantity()));
sale.addDetail(SaleDetail.builder()
        .product(p)
        .quantity(item.quantity())
        .unitPrice(p.getUnitPrice())
        .subtotal(subtotal)
        .build());
total = total.add(subtotal);
```

Al concluir la iteracion, el total acumulado se asigna a la cabecera de la venta: `sale.setTotalAmount(total)`.

### Punto 5: Persistencia de Venta y Movimientos de Inventario (Persist sale and movements)
Por cada articulo procesado exitosamente se registra un movimiento inmutable en la tabla inventory_movements con tipo SALE:

```java
movements.save(InventoryMovement.builder()
        .product(p)
        .movementType(MovementType.SALE)
        .quantity(item.quantity())
        .date(OffsetDateTime.now())
        .user("pos-terminal")
        .build());
```

Posteriormente, se guarda la entidad Sale, la cual propaga en cascada (CascadeType.ALL) la creacion de todos los registros en sale_details.

---

## Estructura de Entidades y Modelado Relacional

### Tabla sales (Cabecera)
* id: Clave primaria autoincremental (BIGINT IDENTITY).
* sale_date: Fecha y hora exacta de la operacion con zona horaria (DATETIMEOFFSET).
* total_amount: Monto monetario total de la transaccion (DECIMAL(12,2)).

### Tabla sale_details (Detalle / Renglones)
* id: Clave primaria autoincremental (BIGINT IDENTITY).
* sale_id: Clave foranea que referencia a sales(id).
* product_id: Clave foranea que referencia a products(id).
* quantity: Cantidad vendida (INT, restringida por CHECK quantity > 0).
* unit_price: Precio unitario congelado al momento de la venta (DECIMAL(12,2)).
* subtotal: Importe calculado del renglon (DECIMAL(12,2)).

---

## Pruebas Unitarias Automatizadas

La suite SaleServiceTest valida cada uno de los requisitos mediante 8 casos de prueba con Mockito:

1. create_multiItemSale_success: Valida la venta multi-articulo, el calculo de subtotales, la reduccion de existencias en ambos productos y la creacion de movimientos tipo SALE.
2. create_inactiveProduct_throwsBusinessException: Comprueba que productos inactivos son rechazados sin alterar stock ni persistir ventas.
3. create_insufficientStock_throwsBusinessException: Comprueba que ventas con cantidad superior a las existencias son rechazadas.
4. create_productNotFound_throwsNotFoundException: Valida el error 404 cuando un identificador de producto no existe.
5. create_emptyBasket_throwsBusinessException: Comprueba que una venta sin articulos es rechazada.
6. list_returnsAllSales: Valida la consulta de ventas realizadas.
7. get_saleExists_returnsResponse: Valida la recuperacion de una venta por identificador.
8. get_saleNotFound_throwsNotFoundException: Valida el error 404 al solicitar una venta inexistente.

Comando para ejecutar la suite de pruebas del Laboratorio 4:
```powershell
mvn test -Dtest=SaleServiceTest
```

Resultado obtenido:
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS.

---

## Guia de Pruebas en Swagger UI / HTTP Client

URL de Swagger UI: http://localhost:8080/swagger-ui.html
Credenciales Basic Auth: admin / admin123

### Escenario 1: Procesar Venta Multi-articulo Valida
Endpoint: POST /api/v1/sales

Cuerpo de la solicitud (JSON):
```json
{
  "items": [
    { "productId": 1, "quantity": 5 },
    { "productId": 2, "quantity": 3 }
  ]
}
```

Calculo esperado para datos semilla:
* Producto 1 (APL-001 - Manzanas): Precio 2.99 * 5 = 14.95. Stock se reduce en 5 unidades.
* Producto 2 (MLK-001 - Leche): Precio 1.79 * 3 = 5.37. Stock se reduce en 3 unidades.
* Total General: 14.95 + 5.37 = 20.32.

Respuesta esperada (HTTP 201 Created):
```json
{
  "id": 1,
  "saleDate": "2026-09-29T23:40:00Z",
  "totalAmount": 20.32,
  "details": [
    {
      "productId": 1,
      "sku": "APL-001",
      "productName": "Red Apples",
      "quantity": 5,
      "unitPrice": 2.99,
      "subtotal": 14.95
    },
    {
      "productId": 2,
      "sku": "MLK-001",
      "productName": "Whole Milk",
      "quantity": 3,
      "unitPrice": 1.79,
      "subtotal": 5.37
    }
  ]
}
```

### Escenario 2: Rechazo por Producto Inactivo
Endpoint: POST /api/v1/sales

Si el producto 1 fue previamente desactivado (DELETE /api/v1/products/1), el intento de venta respondera:

Respuesta esperada (HTTP 400 Bad Request):
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Product is not active: APL-001"
}
```

### Escenario 3: Rechazo por Stock Insuficiente
Endpoint: POST /api/v1/sales

Cuerpo de la solicitud (JSON):
```json
{
  "items": [
    { "productId": 3, "quantity": 1000 }
  ]
}
```

Respuesta esperada (HTTP 400 Bad Request):
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient stock for SKU BRD-001 (available: 30, requested: 1000)"
}
```

### Escenario 4: Verificacion del Historial de Movimientos
Endpoint: GET /api/v1/inventory/movements/1

Respuesta esperada (HTTP 200 OK):
Devuelve una lista que contiene el movimiento correspondiente a la venta:
```json
{
  "productId": 1,
  "sku": "APL-001",
  "movementType": "SALE",
  "quantity": 5,
  "user": "pos-terminal"
}
```

Esto confirma que el criterio de aceptacion ("Sale is created and inventory is reduced for every item") se cumple de manera verificable y transaccional.
