# Hands-on Lab 2: Product CRUD Vertical Slice

**Módulo:** Grocery Inventory POS Microservice Master Class  
**Objetivo:** Desarrollar y verificar una rebanada vertical completa (*Vertical Slice*) para la entidad **Product**, abarcando desde la capa de persistencia (Entity, Repository), transferencia (DTOs, MapStruct Mapper), lógica de negocio transaccional (Service), exposición de API REST (Controller) hasta la verificación interactiva en Swagger UI y pruebas unitarias automatizadas.

---

## Criterios de Aceptación (Acceptance Criteria)

> **"Product can be created, searched, updated, and deactivated."**
> 
> El microservicio debe permitir:
> 1. **Crear** un producto nuevo validando unicidad de SKU, existencia de categoría y reglas de validación en precios y stock.
> 2. **Buscar y listar** productos por término (`q`), filtrando por nombre o SKU de manera insensible a mayúsculas/minúsculas.
> 3. **Actualizar** los campos editables de un producto existente asegurando consistencia referencial.
> 4. **Desactivar** lógicamente un producto (cambio de estado a `INACTIVE` en lugar de borrado físico para preservar la integridad histórica de ventas e inventario).
> 5. **Interactuar y verificar** todos los endpoints a través de **Swagger UI** (`/swagger-ui.html`).

---

## Implementación por Capas del Laboratorio 2

### 1. Entidad y DTOs

#### Entidad de Dominio: `Product.java`
* Mapeo JPA contra la tabla `products`.
* Asociación `@ManyToOne(fetch = FetchType.LAZY)` obligatoria con `Category.java`.
* Restricciones de validación de Java Bean (`@NotBlank`, `@DecimalMin("0.00")`, `@Min(0)`).
* Métodos de dominio enriquecido: `increaseStock(int q)` y `decreaseStock(int q)`.
* Estado gobernado por el enum `ProductStatus.java` (`ACTIVE`, `INACTIVE`).

#### DTOs Inmutables: `ProductDtos.java`
Implementados mediante Java 21 Records:
* **`ProductRequest`**: Payload de entrada con validaciones Jakarta Bean Validation:
  ```java
  public record ProductRequest(
      @NotBlank String sku,
      @NotBlank String name,
      String description,
      @NotNull Long categoryId,
      @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
      @Min(0) int currentStock,
      @Min(0) int reorderLevel,
      @NotNull ProductStatus status
  ) {}
  ```
* **`ProductResponse`**: Proyección segura hacia el exterior, desacoplando la entidad JPA y exponiendo `categoryId` y `categoryName`.

---

### 2. Repositorio y Mapper

#### Repositorio Spring Data JPA: `ProductRepository.java`
* Métodos derivados: `findBySku(String sku)`, `existsBySku(String sku)`.
* Consulta JPQL para búsqueda combinada insensible a mayúsculas:
  ```java
  @Query("select p from Product p where lower(p.name) like lower(concat('%', :term, '%')) or lower(p.sku) like lower(concat('%', :term, '%'))")
  List<Product> search(@Param("term") String term);
  ```

#### Mapper con MapStruct: `ProductMapper.java`
* Genera implementaciones en tiempo de compilación con cero sobrecarga en tiempo de ejecución.
* Mapea campos anidados:
  ```java
  @Mapping(source = "category.id", target = "categoryId")
  @Mapping(source = "category.name", target = "categoryName")
  ProductResponse toResponse(Product product);
  ```

---

### 3. Servicio de Negocio Transaccional: `ProductService.java`

Aplica las reglas de negocio del sistema:
* **Creación:** Valida que el SKU no exista previamente en catálogo (`BusinessException`), verifica que la categoría exista en base de datos (`NotFoundException`), asocia la entidad y guarda.
* **Búsqueda/Listado:** Si el término `q` es nulo o vacío devuelve el catálogo completo; de lo contrario delega la búsqueda al query JPQL.
* **Actualización:** Busca el producto existente, valida que un cambio de SKU no choque con el de otro producto, actualiza los atributos y persiste.
* **Desactivación (Baja Lógica):** Cambia el estado del producto a `ProductStatus.INACTIVE` sin eliminar el registro físico.

---

### 4. Controlador REST: `ProductController.java`

Expone la API RESTful bajo la ruta base `/api/v1/products`:

| Método HTTP | Ruta | Descripción | Código de Estado |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/products` | Crear nuevo producto con `@Valid` | `201 CREATED` |
| `GET` | `/api/v1/products?q={term}` | Listar todos o buscar por término | `200 OK` |
| `GET` | `/api/v1/products/{id}` | Obtener detalle de un producto por ID | `200 OK` |
| `PUT` | `/api/v1/products/{id}` | Actualizar datos de un producto | `200 OK` |
| `DELETE` | `/api/v1/products/{id}` | Desactivación lógica (baja) | `204 NO CONTENT` |

---

## Pruebas Unitarias Automatizadas

Para garantizar y demostrar el cumplimiento de los criterios de aceptación sin requerir dependencias externas, se implementó la suite completa de pruebas unitarias en `ProductServiceTest.java`

1. `create_success`: Creación exitosa con categoría vinculada.
2. `create_duplicateSku_throwsBusinessException`: Rechazo ante SKU duplicado.
3. `create_categoryNotFound_throwsNotFoundException`: Rechazo si la categoría no existe.
4. `list_withoutTerm_returnsAllProducts`: Listado de todo el catálogo.
5. `list_withTerm_returnsMatchingProducts`: Filtrado de productos por término de búsqueda.
6. `get_success`: Obtención de producto por ID.
7. `get_notFound_throwsException`: Manejo de excepción cuando el ID no existe.
8. `update_success`: Modificación exitosa de propiedades y categoría.
9. `delete_deactivatesProduct`: Verificación de baja lógica (`ACTIVE` -> `INACTIVE`).

### Ejecución de Pruebas Unitarias:
```powershell
mvn test -Dtest=ProductServiceTest
```
*Resultado:* **9 tests run, 0 failures, 0 errors, BUILD SUCCESS**.

---

## Paso a Paso: Probar en Swagger UI

### 1. Iniciar la Aplicación

Asegúrate de que SQL Server esté corriendo en segundo plano:
```powershell
docker compose up -d sqlserver
```

Inicia la aplicación Spring Boot desde tu IDE o vía terminal:
```powershell
mvn spring-boot:run
```

### 2. Acceder a Swagger UI
Abre en tu navegador web:
👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)** (o `/swagger-ui.html`).

* **Autenticación:** En la ventana emergente o haciendo clic en el candado **Authorize**, ingresa las credenciales Basic Auth:
  * **Usuario:** `admin`
  * **Contraseña:** `admin123`

---

### 3. Guía de Ejecución de Endpoints en Swagger

#### A. Crear Producto (`POST /api/v1/products`)
En Swagger UI, expande `POST /api/v1/products`, haz clic en **Try it out** e ingresa el siguiente JSON:

```json
{
  "sku": "BAN-001",
  "name": "Organic Bananas",
  "description": "Bunch of fresh organic bananas",
  "categoryId": 1,
  "unitPrice": 1.49,
  "currentStock": 75,
  "reorderLevel": 15,
  "status": "ACTIVE"
}
```
*Haz clic en **Execute**.*  
*Respuesta esperada:* Código **`201 Created`** con el producto asignado a un nuevo ID numérico.

---

#### B. Buscar Productos (`GET /api/v1/products`)
Expande `GET /api/v1/products`, haz clic en **Try it out**:
* Deja el parámetro `q` vacío para ver todos los productos sembrados (`APL-001`, `MLK-001`, `BRD-001`, `RCE-001` y `BAN-001`).
* Escribe `banana` o `BAN` en el parámetro `q` y haz clic en **Execute**.  
*Respuesta esperada:* Código **`200 OK`** devolviendo únicamente el registro de las bananas.

---

#### C. Actualizar Producto (`PUT /api/v1/products/{id}`)
Expande `PUT /api/v1/products/{id}`:
* En `id`, ingresa el ID del producto creado (ej. `5`).
* En el cuerpo (body):
```json
{
  "sku": "BAN-001",
  "name": "Organic Cavendish Bananas",
  "description": "Bunch of ripe organic bananas (Premium)",
  "categoryId": 1,
  "unitPrice": 1.89,
  "currentStock": 60,
  "reorderLevel": 15,
  "status": "ACTIVE"
}
```
*Respuesta esperada:* Código **`200 OK`** con los campos `name` y `unitPrice` actualizados.

---

#### D. Desactivar Producto (`DELETE /api/v1/products/{id}`)
Expande `DELETE /api/v1/products/{id}`:
* En `id`, ingresa `5`.
* Haz clic en **Execute**.  
*Respuesta esperada:* Código **`204 No Content`**.

---

#### E. Validar la Desactivación (`GET /api/v1/products/{id}`)
Consulta nuevamente el producto con `GET /api/v1/products/5`.  
*Respuesta esperada:* El campo `"status"` ahora devuelve **`"INACTIVE"`**, confirmando la baja lógica sin pérdida de integridad de datos.
