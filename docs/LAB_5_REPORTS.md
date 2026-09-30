# Hands-on Lab 5: Reports

Modulo: Grocery Inventory POS Microservice Master Class
Objetivo: Implementar y verificar los servicios y endpoints de reportes analiticos y operativos del sistema, respondiendo preguntas criticas de negocio sobre valorizacion total de inventario, deteccion temprana de productos con existencias por debajo del punto de reorden, consolidacion de ventas diarias y auditoria historica de movimientos.

---

## Criterios de Aceptacion (Acceptance Criteria)

"Reports answer operational questions from persisted data."

El sistema debe responder a las siguientes preguntas operativas a partir de los datos persistidos en SQL Server:
1. Cuales productos requieren reabastecimiento urgente por encontrarse en o por debajo de su umbral critico de reorden?
2. Cual es la valorizacion economica actual del stock disponible en tienda y almacen por producto y categoria?
3. Cuantas transacciones de venta y que volumen total monetario se registro en una fecha calendario determinada?
4. Cual es la trazabilidad cronologica detallada de entradas, salidas y ventas de un producto particular?

---

## Resolucion de los 4 Puntos del Laboratorio

### Punto 1: Reporte de Stock Bajo (Low stock report)
El metodo `lowStock()` de ReportService localiza los productos que requieren orden de compra o reabastecimiento:

* Regla de negocio: Producto con estado `ProductStatus.ACTIVE` y cuya existencia actual cumpla `currentStock <= reorderLevel`.
* Optimizacion de consulta: Se implemento en ProductRepository la consulta JPQL con carga ansiosa controlada (`join fetch`):
  ```sql
  select p from Product p join fetch p.category where p.status = :status and p.currentStock <= p.reorderLevel
  ```
  Esto previene el problema de rendimiento N+1 al consultar la entidad Category asociada.
* Endpoint expuesto: `GET /api/v1/reports/low-stock`.

### Punto 2: Reporte de Valorizacion de Inventario (Inventory value report)
El metodo `inventory()` de ReportService calcula el activo circulante representado en existencias para todo el catalogo:

* Regla de calculo: Para cada producto se multiplica su precio unitario por la cantidad en stock actual:
  ```java
  BigDecimal stockValue = p.getUnitPrice().multiply(BigDecimal.valueOf(p.getCurrentStock()));
  ```
* Se emplea la consulta `findAllWithCategory()` con `join fetch` para traer la categoria en una unica sentencia SQL.
* Endpoint expuesto: `GET /api/v1/reports/inventory`.

### Punto 3: Reporte de Ventas Diarias (Daily sales report)
El metodo `daily(LocalDate date)` agrega la actividad comercial ocurrida en un intervalo de 24 horas:

* Delimitacion temporal precisa: Se calcula el rango de busqueda entre las 00:00:00 UTC del dia solicitado y las 00:00:00 UTC del dia posterior:
  ```java
  var from = date.atStartOfDay().atOffset(ZoneOffset.UTC);
  var to = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
  ```
* Agregacion: Consulta las ventas con `sales.findSalesBetween(from, to)`, cuenta las transacciones (`rows.size()`) y suma los importes totales con `BigDecimal::add`.
* Si no existen ventas registradas en esa fecha, retorna 0 transacciones y 0.00 en total sin arrojar errores.
* Endpoint expuesto: `GET /api/v1/reports/daily-sales?date=YYYY-MM-DD` (si no se proporciona el parametro de fecha, toma por defecto la fecha actual).

### Punto 4: Endpoint de Historial de Movimientos (Movement history endpoint)
Permite auditar el libro mayor (Ledger) de un producto determinado:

* Integra y delega la consulta a `inventoryService.history(productId)`.
* Ordenamiento: Garantizado en orden cronologico descendente (`movement_date DESC`) respaldado por el indice relacional `ix_movements_product_date`.
* Endpoint expuesto: `GET /api/v1/reports/movements/{productId}` (ademas de su acceso operativo en `/api/v1/inventory/movements/{productId}`).

---

## Optimizaciones de Arquitectura y Persistencia

1. Modificadores de Transaccion de Solo Lectura:
   Todos los metodos de lectura en ReportService estan anotados con `@Transactional(readOnly = true)`. Esto instruye a Hibernate para omitir la inicializacion del mecanismo de comprobacion de cambios (dirty checking), reduciendo el consumo de memoria y optimizando el tiempo de respuesta.

2. Eliminacion de Consultas N+1:
   Al usar `join fetch p.category`, se garantiza que una solicitud que liste cientos de productos ejecute unicamente 1 consulta a la base de datos con un INNER JOIN, en lugar de 1 consulta para productos mas N consultas adicionales para obtener el nombre de cada categoria.

---

## Pruebas Unitarias Automatizadas

La suite ReportServiceTest valida todos los escenarios operativos mediante 5 pruebas con Mockito y AssertJ:

1. inventory_calculatesStockValueCorrectly: Valida el calculo matematico de valorizacion de stock para multiples productos.
2. lowStock_returnsOnlyActiveProductsBelowOrAtReorderLevel: Valida que solo los productos con stock critico sean incluidos en la alerta.
3. daily_calculatesTransactionsAndTotalAmount: Valida la sumatoria de ingresos y el conteo de ventas en un dia con transacciones.
4. daily_noSales_returnsZeroTotals: Valida el manejo correcto de dias sin transacciones retornando ceros consistentes.
5. movements_delegatesToInventoryService: Valida la delegacion y recuperacion del historial de movimientos.

Comando para ejecutar la suite de pruebas del Laboratorio 5:
```powershell
mvn test -Dtest=ReportServiceTest
```

Resultado obtenido:
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS.

---

## Guia de Pruebas en Swagger UI / HTTP Client

URL de Swagger UI: http://localhost:8080/swagger-ui.html
Credenciales Basic Auth: admin / admin123

### Escenario 1: Consultar Reporte de Stock Bajo
Endpoint: GET /api/v1/reports/low-stock

Respuesta esperada (HTTP 200 OK):
Devuelve los productos cuyo stock actual es menor o igual a su umbral de reorden (por ejemplo, el producto RCE-001 de los datos semilla, con stock = 12 y reorderLevel = 15):
```json
[
  {
    "productId": 4,
    "sku": "RCE-001",
    "name": "Rice",
    "category": "Pantry",
    "stock": 12,
    "reorderLevel": 15,
    "stockValue": 27.48
  }
]
```

### Escenario 2: Consultar Valorizacion Total de Inventario
Endpoint: GET /api/v1/reports/inventory

Respuesta esperada (HTTP 200 OK):
Lista todos los productos del catalogo con su stock actual y la valuacion economica calculada:
```json
[
  {
    "productId": 1,
    "sku": "APL-001",
    "name": "Red Apples",
    "category": "Produce",
    "stock": 120,
    "reorderLevel": 25,
    "stockValue": 358.80
  },
  {
    "productId": 2,
    "sku": "MLK-001",
    "name": "Whole Milk",
    "category": "Dairy",
    "stock": 80,
    "reorderLevel": 20,
    "stockValue": 143.20
  }
]
```

### Escenario 3: Consultar Ventas Diarias
Endpoint: GET /api/v1/reports/daily-sales?date=2026-09-29

Respuesta esperada (HTTP 200 OK):
```json
{
  "date": "2026-09-29",
  "transactions": 1,
  "totalSales": 20.32
}
```

### Escenario 4: Consultar Trazabilidad de Movimientos de un Producto
Endpoint: GET /api/v1/reports/movements/1

Respuesta esperada (HTTP 200 OK):
```json
[
  {
    "id": 2,
    "productId": 1,
    "sku": "APL-001",
    "movementType": "SALE",
    "quantity": 5,
    "date": "2026-09-29T23:40:00Z",
    "user": "pos-terminal"
  },
  {
    "id": 1,
    "productId": 1,
    "sku": "APL-001",
    "movementType": "REPLENISHMENT",
    "quantity": 50,
    "date": "2026-09-29T23:25:00Z",
    "user": "warehouse-staff-01"
  }
]
```

Esta salida valida que los reportes responden satisfactoriamente a las preguntas operativas planteadas en los criterios de aceptacion a partir de los datos persistidos en el sistema.
