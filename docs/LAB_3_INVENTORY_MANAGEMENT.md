# Hands-on Lab 3: Inventory Replenishment and Adjustment

**Módulo:** Grocery Inventory POS Microservice Master Class  
**Objetivo:** Desarrollar la lógica transaccional de control y auditoría de inventario en `InventoryService`, asegurando que cada mutación física de stock genere un registro inmutable en el historial de movimientos (`inventory_movements`), bloqueando tipos de ajuste inválidos y evitando que los ajustes de salida excedan las existencias físicas disponibles.

---

## Criterios de Aceptación (Acceptance Criteria)

> **"Stock changes and movement history prove the mutation."**
> 
> El microservicio debe garantizar que:
> 1. Toda alteración de stock (abastecimiento o ajuste) se ejecute de forma atómica y transaccional (`@Transactional`).
> 2. Cada mutación genere una fila de auditoría con tipo, cantidad, fecha/hora exacta con zona horaria (`DATETIMEOFFSET`) y usuario responsable.
> 3. Se rechacen tipos de ajuste distintos de `ADJUSTMENT_IN` y `ADJUSTMENT_OUT`.
> 4. Se impidan ajustes de salida (`ADJUSTMENT_OUT`) que sobrepasen el stock disponible (evitando stock negativo).
> 5. La consulta del historial (`/api/v1/inventory/movements/{productId}`) demuestre la trazabilidad cronológica de todas las mutaciones.

---

## Resolución de los 4 Puntos del Laboratorio

### Punto 1: Implementar `InventoryService`
El servicio `InventoryService.java` centraliza la lógica de mutación en el método transaccional `move(...)`:
* Gestiona la atomicidad de las operaciones mediante `@Transactional`: si la escritura del movimiento falla o la regla de stock no se cumple, la modificación del producto se revierte automáticamente (*Rollback*).
* Valida que el producto exista y se encuentre en estado `ProductStatus.ACTIVE`.

### Punto 2: Registrar filas de movimiento para cada mutación (*Write movement rows for every mutation*)
* Toda operación crea un registro en la tabla `inventory_movements` representado por `InventoryMovement.java`:
  * `movementType`: Enum `MovementType.java` (`REPLENISHMENT`, `ADJUSTMENT_IN`, `ADJUSTMENT_OUT`, `SALE`).
  * `quantity`: Cantidad afectada (restringida a valores positivos en base de datos mediante `ck_movements_qty`).
  * `date`: Marca temporal exacta `OffsetDateTime.now()`.
  * `user`: Cadena con la identidad del operario o sistema que ejecutó la acción.

### Punto 3: Bloquear tipos de ajuste inválidos (*Block invalid adjustment type*)
El endpoint `/api/v1/inventory/adjustment` únicamente permite correcciones manuales de stock:
```java
if (c.type() != MovementType.ADJUSTMENT_IN && c.type() != MovementType.ADJUSTMENT_OUT) {
    throw new BusinessException("Invalid adjustment type: " + c.type() + ". Only ADJUSTMENT_IN and ADJUSTMENT_OUT are permitted.");
}
```
*Si un cliente intenta enviar `REPLENISHMENT` o `SALE` a través de la ruta de ajuste, la solicitud se aborta inmediatamente con HTTP `400 Bad Request`.*

### Punto 4: Bloquear ajustes de salida que excedan el stock (*Block adjustment out beyond stock*)
Antes de reducir el inventario físico, el servicio verifica la disponibilidad:
```java
if (p.getCurrentStock() < qty) {
    throw new BusinessException("Insufficient stock for product " + p.getSku()
            + " (available: " + p.getCurrentStock() + ", requested: " + qty + ")");
}
p.decreaseStock(qty);
```
*Esto protege la regla de integridad de negocio y evita que se active la restricción de base de datos `ck_products_stock` (`current_stock >= 0`), manteniendo el error a nivel de dominio con una respuesta descriptiva.*

---

## Pruebas Unitarias Automatizadas

Se diseñó la suite completa de pruebas unitarias en **`InventoryServiceTest.java`** empleando Mockito y AssertJ:

1. `replenish_success`: Verifica que el stock se incremente y se persista un movimiento `REPLENISHMENT`.
2. `adjust_in_success`: Verifica el incremento de stock mediante `ADJUSTMENT_IN`.
3. `adjust_out_success`: Verifica la reducción de stock mediante `ADJUSTMENT_OUT` cuando hay saldo suficiente.
4. `adjust_invalidType_throwsBusinessException`: Comprueba el bloqueo inmediato ante tipos de ajuste no autorizados.
5. `adjust_outBeyondStock_throwsBusinessException`: Comprueba el bloqueo ante salidas superiores al stock disponible.
6. `move_productNotFound_throwsException`: Valida el lanzamiento de `NotFoundException` ante un ID inexistente.
7. `move_inactiveProduct_throwsException`: Impide la mutación de inventario en productos dados de baja (`INACTIVE`).
8. `history_success`: Verifica la recuperación del historial ordenado cronológicamente descendente.
9. `history_productNotFound_throwsException`: Valida el control de error si se solicita historial de un producto inexistente.

### Ejecución de Pruebas:
```powershell
mvn test -Dtest=InventoryServiceTest
```
*Resultado:* **9 tests run, 0 failures, 0 errors, BUILD SUCCESS**.

---

## Guía de Pruebas Interactivas en Swagger UI

Con la base de datos levantada (`docker compose up -d sqlserver`) y la aplicación iniciada (`mvn spring-boot:run`):

Abre: **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**  
*(Autenticación: `admin` / `admin123`)*.

---

### Escenario 1: Reabastecimiento de Stock (`POST /api/v1/inventory/replenishment`)
* **Propósito:** Ingresar 50 unidades al producto `APL-001` (ID `1`, stock inicial = 120).
* **Payload:**
  ```json
  {
    "productId": 1,
    "quantity": 50,
    "user": "warehouse-staff-01"
  }
  ```
* **Respuesta esperada (`201 Created`):**
  ```json
  {
    "id": 1,
    "productId": 1,
    "sku": "APL-001",
    "movementType": "REPLENISHMENT",
    "quantity": 50,
    "date": "2026-09-29T23:25:00Z",
    "user": "warehouse-staff-01"
  }
  ```
* *El stock de `APL-001` pasa de 120 a 170.*

---

### Escenario 2: Ajuste de Salida por Merma (`POST /api/v1/inventory/adjustment`)
* **Propósito:** Descontar 10 unidades por producto dañado en tienda.
* **Payload:**
  ```json
  {
    "productId": 1,
    "quantity": 10,
    "type": "ADJUSTMENT_OUT",
    "user": "quality-auditor"
  }
  ```
* **Respuesta esperada (`201 Created`):**
  * `movementType`: `"ADJUSTMENT_OUT"`
  * `quantity`: `10`
* *El stock de `APL-001` pasa de 170 a 160.*

---

### Escenario 3: Bloqueo de Ajuste Inválido (Regla de Negocio)
* **Propósito:** Intentar registrar un tipo de movimiento no permitido en `/adjustment`.
* **Payload:**
  ```json
  {
    "productId": 1,
    "quantity": 10,
    "type": "REPLENISHMENT",
    "user": "malicious-actor"
  }
  ```
* **Respuesta esperada (`400 Bad Request`):**
  ```json
  {
    "status": 400,
    "error": "Bad Request",
    "message": "Invalid adjustment type: REPLENISHMENT. Only ADJUSTMENT_IN and ADJUSTMENT_OUT are permitted."
  }
  ```

---

### Escenario 4: Bloqueo de Salida Superior al Stock Disponible
* **Propósito:** Intentar ajustar hacia fuera 500 unidades cuando solo hay 160.
* **Payload:**
  ```json
  {
    "productId": 1,
    "quantity": 500,
    "type": "ADJUSTMENT_OUT",
    "user": "store-manager"
  }
  ```
* **Respuesta esperada (`400 Bad Request`):**
  ```json
  {
    "status": 400,
    "error": "Bad Request",
    "message": "Insufficient stock for product APL-001 (available: 160, requested: 500)"
  }
  ```

---

### Escenario 5: Comprobación del Historial de Movimientos (`GET /api/v1/inventory/movements/{productId}`)
* **Propósito:** Consultar el libro mayor (*Ledger*) de auditoría del producto `1`.
* **Petición:** `GET /api/v1/inventory/movements/1`
* **Respuesta esperada (`200 OK`):**
  Una lista ordenada cronológicamente con:
  1. Movimiento `ADJUSTMENT_OUT` (10 unidades).
  2. Movimiento `REPLENISHMENT` (50 unidades).
* **Confirmación del Criterio de Aceptación:** El historial y el stock actual (160 unidades) prueban fehacientemente cada una de las mutaciones realizadas.
