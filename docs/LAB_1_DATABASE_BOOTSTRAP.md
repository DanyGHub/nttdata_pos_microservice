# Hands-on Lab 1: Database Bootstrap

**Módulo:** Grocery Inventory POS Microservice Master Class  
**Objetivo:** Desplegar y aprovisionar la base de datos relacional Microsoft SQL Server 2022 en un contenedor Docker, ejecutar la estructura de tablas (DDL), poblar los datos semilla (DML) y verificar la integridad referencial y restricciones (constraints) del modelo.

---

## Criterios de Aceptación (Acceptance Criteria)

> **"Students can query seed products and explain constraints."**
> 
> Los estudiantes o desarrolladores son capaces de:
> 1. Iniciar el motor de base de datos SQL Server 2022 en Docker de forma saludable.
> 2. Ejecutar y comprender el script de esquema (`01-schema.sql`).
> 3. Ejecutar el script de carga inicial o semilla (`02-seed-data.sql`).
> 4. Consultar y verificar las categorías y productos insertados.
> 5. Explicar el propósito técnico de cada una de las restricciones (*constraints* y *checks*) definidas en el modelo.

---

### Paso 1: Iniciar el Contenedor de SQL Server

Se utiliza la imagen oficial `mcr.microsoft.com/mssql/server:2022-latest`.

#### Configuración Clave:
* **`ACCEPT_EULA: "Y"`**: Aceptación obligatoria del acuerdo de licencia de Microsoft.
* **`MSSQL_SA_PASSWORD: "YourStrongPassword123!"`**: Contraseña del usuario administrador (`sa`), cumpliendo directivas de complejidad de SQL Server.
* **`MSSQL_PID: "Developer"`**: Edición Developer para desarrollo local sin costo de licenciamiento.
* **Puerto `1433:1433`**: Mapeo del puerto TDS estándar al host.

#### Comandos de Ejecución:

* **Opción A (Recomendada - Vía Docker Compose):**
  ```powershell
  # Levanta el servicio de base de datos en segundo plano
  docker compose up -d sqlserver
  ```

* **Opción B (Automatizada con el inicializador):**
  ```powershell
  # Levanta la base de datos y ejecuta automáticamente la inicialización (schema + seed)
  docker compose up sqlserver-init
  ```

* **Opción C (Vía Docker CLI directa):**
  ```powershell
  docker run -e "ACCEPT_EULA=Y" -e "MSSQL_SA_PASSWORD=YourStrongPassword123!" -e "MSSQL_PID=Developer" -p 1433:1433 --name grocery-sqlserver -d mcr.microsoft.com/mssql/server:2022-latest
  ```

Para verificar que el contenedor se encuentra saludable (`healthy`):
```powershell
docker ps --filter "name=grocery-sqlserver"
```

---

### Paso 2: Ejecutar el Script de Esquema (`01-schema.sql`)

El script `sql/01-schema.sql` crea la base de datos `grocery_inventory` y 5 tablas relacionales:

1. **`categories`**: Catálogo maestro de categorías.
2. **`products`**: Productos del supermercado con SKU, precio, stock y umbral de reorden.
3. **`inventory_movements`**: Registro inmutable de auditoría para entradas, ajustes y salidas de stock.
4. **`sales`**: Cabecera de transacciones de venta en el POS.
5. **`sale_details`**: Detalle o renglones de cada venta asociada a un producto.

#### Ejecución Manual del Script de Esquema:
```powershell
docker exec -i grocery-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "YourStrongPassword123!" -C -i /docker-entrypoint-initdb.d/01-schema.sql
```

---

### Paso 3: Ejecutar el Script de Datos Semilla (`02-seed-data.sql`)

El script `sql/02-seed-data.sql` inserta el catálogo base para pruebas funcionales:

* **Categorías:** `Produce` (Frutas y verduras), `Dairy` (Lácteos), `Bakery` (Panadería), `Pantry` (Abarrotes).
* **Productos:**
  * `APL-001` - Red Apples (1kg bag) | Precio: \$2.99 | Stock: 120 | Reorden: 25 | ACTIVE
  * `MLK-001` - Whole Milk (1L carton) | Precio: \$1.79 | Stock: 80 | Reorden: 20 | ACTIVE
  * `BRD-001` - Sourdough Bread (500g) | Precio: \$3.49 | Stock: 30 | Reorden: 10 | ACTIVE
  * `RCE-001` - Rice (1kg bag) | Precio: \$2.29 | Stock: 12 | Reorden: 15 | ACTIVE

#### Ejecución Manual del Script de Semilla:
```powershell
docker exec -i grocery-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "YourStrongPassword123!" -C -i /docker-entrypoint-initdb.d/02-seed-data.sql
```

*(Nota: Si usas `docker compose up sqlserver-init`, los Pasos 2 y 3 se ejecutan automáticamente a través de [`sql/03-init.sh`](file:///d:/Users/ADLR/NTTDATA/nttdata_pos_microservice/sql/03-init.sh)).*

---

### Paso 4: Validar Categorías/Productos y Explicación de Constraints

Para completar el criterio de aceptación, se ha incorporado el script de validación [`sql/04-validate.sql`](file:///d:/Users/ADLR/NTTDATA/nttdata_pos_microservice/sql/04-validate.sql).

#### Ejecutar la Validación:
```powershell
docker exec -i grocery-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "YourStrongPassword123!" -C -i /docker-entrypoint-initdb.d/04-validate.sql
```

#### Salida Esperada:
```text
>>> 1. Validando Categorias Existentes:
id  name     description
--- -------- ---------------------------
1   Produce  Fresh fruits and vegetables
2   Dairy    Milk, yogurt, cheese
3   Bakery   Bread and pastries
4   Pantry   Packaged goods

>>> 2. Validando Productos y Relacion con Categorias:
product_id sku     product_name    category_name unit_price current_stock reorder_level status
---------- ------- --------------- ------------- ---------- ------------- ------------- ------
1          APL-001 Red Apples      Produce       2.99       120           25            ACTIVE
2          MLK-001 Whole Milk      Dairy         1.79       80            20            ACTIVE
3          BRD-001 Sourdough Bread Bakery        3.49       30            10            ACTIVE
4          RCE-001 Rice            Pantry        2.29       12            15            ACTIVE

>>> 3. Probando Restricciones (Constraints) de forma activa:
[EXITO] Constraint uk_products_sku activo. Rechazo SKU duplicado: Violation of UNIQUE KEY constraint 'uk_products_sku'...
[EXITO] Constraint ck_products_stock activo. Rechazo stock negativo: The INSERT statement conflicted with the CHECK constraint "ck_products_stock"...
[EXITO] Constraint fk_products_categories activo. Rechazo categoria invalida: The INSERT statement conflicted with the FOREIGN KEY constraint "fk_products_categories"...
>>> Validacion completa: todas las tablas, relaciones y constraints funcionan segun el diseno.
```

---

## Explicación Detallada de Constraints del Modelo

El diseño de base de datos aplica reglas estrictas para garantizar integridad y consistencia operativa:

| Tabla | Nombre del Constraint | Tipo | Regla / Definición | Justificación de Negocio |
| :--- | :--- | :--- | :--- | :--- |
| `categories` | `uk_categories_name` | **UNIQUE** | `UNIQUE(name)` | Evita duplicidad de nombres en categorías de productos. |
| `products` | `uk_products_sku` | **UNIQUE** | `UNIQUE(sku)` | El código SKU es el identificador comercial único del producto; no puede repetirse. |
| `products` | `ck_products_stock` | **CHECK** | `CHECK(current_stock >= 0)` | **Regla física:** El inventario de un supermercado no puede tener cantidades físicas negativas. Una venta debe ser rechazada si no hay existencias. |
| `products` | `ck_products_reorder` | **CHECK** | `CHECK(reorder_level >= 0)` | El punto de reorden no puede ser un valor negativo. |
| `products` | `fk_products_categories` | **FOREIGN KEY** | `REFERENCES categories(id)` | Integridad referencial: no pueden existir productos huérfanos sin categoría asignada. |
| `inventory_movements` | `ck_movements_qty` | **CHECK** | `CHECK(quantity > 0)` | Cualquier movimiento (abastecimiento, ajuste o venta) debe involucrar al menos 1 unidad. |
| `inventory_movements` | `fk_movements_products` | **FOREIGN KEY** | `REFERENCES products(id)` | Todo movimiento debe apuntar a un producto existente en catálogo. |
| `sale_details` | `ck_detail_qty` | **CHECK** | `CHECK(quantity > 0)` | No se permiten renglones de venta con cantidad menor o igual a cero. |
| `sale_details` | `fk_details_sales` | **FOREIGN KEY** | `REFERENCES sales(id)` | Cada ítem de venta pertenece estrictamente a una transacción de venta existente. |
| `sale_details` | `fk_details_products` | **FOREIGN KEY** | `REFERENCES products(id)` | El ítem vendido debe existir en el catálogo. |

### Decisiones de Tipos de Datos e Índices:
* **`DECIMAL(12,2)`**: Empleado en `unit_price`, `subtotal` y `total_amount` para evitar errores de redondeo o pérdida de precisión financiera inherentes a los tipos de punto flotante (`FLOAT`/`REAL`).
* **`DATETIMEOFFSET`**: Registra la marca temporal con zona horaria explícita (`movement_date`, `sale_date`), fundamental para auditorías distribuidas.
* **Índices Secundarios**:
  * `ix_products_category`: Acelera los filtrados por categoría.
  * `ix_movements_product_date`: Optimiza consultas del historial de un producto ordenadas cronológicamente (`DESC`).
  * `ix_sales_date`: Optimiza la generación de reportes de ventas diarias.

---

## 🧪 Pruebas de Violación de Restricciones (Validación Práctica)

Para corroborar que los constraints protegen la base de datos, ejecuta estas sentencias en `sqlcmd`:

1. **Intentar registrar un producto con SKU duplicado:**
   ```sql
   INSERT INTO products(sku, name, category_id, unit_price, current_stock, reorder_level, status)
   VALUES('APL-001', 'Manzana Verde', 1, 3.50, 10, 5, 'ACTIVE');
   ```
   *Respuesta esperada:* `Violation of UNIQUE KEY constraint 'uk_products_sku'`.

2. **Intentar registrar stock negativo:**
   ```sql
   INSERT INTO products(sku, name, category_id, unit_price, current_stock, reorder_level, status)
   VALUES('ERR-001', 'Producto Negativo', 1, 1.00, -5, 5, 'ACTIVE');
   ```
   *Respuesta esperada:* `The INSERT statement conflicted with the CHECK constraint "ck_products_stock"`.

3. **Intentar asociar a una categoría inexistente:**
   ```sql
   INSERT INTO products(sku, name, category_id, unit_price, current_stock, reorder_level, status)
   VALUES('ERR-002', 'Producto Huerfano', 9999, 1.00, 10, 5, 'ACTIVE');
   ```
   *Respuesta esperada:* `The INSERT statement conflicted with the FOREIGN KEY constraint "fk_products_categories"`.
