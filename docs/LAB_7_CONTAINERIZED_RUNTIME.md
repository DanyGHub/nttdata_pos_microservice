# Hands-on Lab 7: Containerized Runtime

Modulo: Grocery Inventory POS Microservice Master Class
Objetivo: Empaquetar y ejecutar la arquitectura completa en contenedores de produccion mediante Docker y Docker Compose, demostrando que una maquina limpia con unicamente Docker instalado puede compilar, inicializar la base de datos relacional, arrancar el microservicio, verificar la disponibilidad del sistema y procesar operaciones de venta de extremo a extremo.

---

## Criterios de Aceptacion (Acceptance Criteria)

"A clean machine with Docker can run the full stack."

El entorno debe garantizar que:
1. La imagen Docker de la aplicacion se compile mediante una construccion multi-etapa (Multi-Stage Build) que aisle el entorno de compilacion (JDK + Maven) del artefacto final de ejecucion (JRE ligero).
2. Docker Compose orqueste los tres servicios indispensables: motor de base de datos SQL Server 2022, inicializador de esquema y datos semilla, y el microservicio Spring Boot.
3. El endpoint de diagnostico `/actuator/health` responda confirmando el estado operativo activo (UP).
4. La documentacion interactiva de OpenAPI sea accesible en Swagger UI (`/swagger-ui.html`).
5. Se procese una venta real de multiples articulos directamente sobre el contenedor, afectando el inventario y registrando los movimientos en la base de datos contenerizada.

---

## Resolucion de los 5 Puntos del Laboratorio

### Punto 1: Construir la Imagen Docker (Build the Docker image)
El archivo Dockerfile implementa un Multi-Stage Build optimizado:

1. Etapa de Compilacion (Build Stage):
   * Imagen base: `maven:3.9.11-eclipse-temurin-21`.
   * Cache de dependencias: Ejecuta `mvn dependency:go-offline` copiando unicament el `pom.xml` para aprovechar la cache de capas de Docker.
   * Compilacion: Copia el directorio `src/` y genera el JAR ejecutable con `mvn clean package -DskipTests`.

2. Etapa de Ejecucion (Runtime Stage):
   * Imagen base: `eclipse-temurin:21-jre-alpine` (reducida huella de memoria y tamano).
   * Seguridad: Crea un usuario y grupo sin privilegios de root (`app`).
   * Parametros de memoria JVM: `-XX:MaxRAMPercentage=75` para respetar los limites de recursos asignados al contenedor.
   * Puerto expuesto: 8080.

3. Optimizacion de Contexto (.dockerignore):
   Se incorporo `.dockerignore` para evitar el envio de directorios locales redundantes (`target/`, `.git/`, `docs/`) al daemon de Docker durante la construccion.

Comando para compilar la imagen:
```powershell
docker compose build
```

### Punto 2: Iniciar Docker Compose (Start docker compose)
El archivo docker-compose.yml coordina la inicializacion con dependencias condicionadas:

1. Servicio `sqlserver`:
   * Imagen: `mcr.microsoft.com/mssql/server:2022-latest`.
   * Contenedor: `grocery-sqlserver`.
   * Volumen persistente: `sqlserver_data:/var/opt/mssql`.
   * Healthcheck: Ejecuta periodicamente `sqlcmd -Q 'SELECT 1'` hasta confirmar que el motor esta listo para recibir conexiones.

2. Servicio `sqlserver-init`:
   * Inicializador efimero que comparte la red del servidor (`network_mode: "service:sqlserver"`).
   * Se dispara unicamente cuando `sqlserver` esta saludable (`condition: service_healthy`).
   * Ejecuta `03-init.sh`, aprovisionando las tablas (`01-schema.sql`) y los datos semilla (`02-seed-data.sql`).

3. Servicio `inventory-service`:
   * Se inicia unicamente tras la culminacion exitosa del inicializador (`condition: service_completed_successfully`).
   * Se conecta a traves de la variable `SPRING_DATASOURCE_URL=jdbc:sqlserver://sqlserver:1433;databaseName=grocery_inventory;...`.

Comando para iniciar todo el stack en segundo plano:
```powershell
docker compose up -d
```

### Punto 3: Validar el Endpoint de Salud (Validate health endpoint)
Una vez levantado el stack, se verifica el estado operativo de la aplicacion y su conectividad contra la base de datos a traves de Spring Boot Actuator.

Comando en PowerShell:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get
```

O mediante cURL:
```bash
curl -i http://localhost:8080/actuator/health
```

Respuesta esperada (HTTP 200 OK):
```json
{
  "status": "UP"
}
```

### Punto 4: Abrir Swagger UI (Open Swagger)
La interfaz grafica interactiva de Swagger UI se encuentra disponible en:
URL: http://localhost:8080/swagger-ui/index.html (o `/swagger-ui.html`)

Procedimiento de autenticacion:
1. Hacer clic en el boton "Authorize".
2. Ingresar credenciales Basic Auth:
   * Usuario: `admin`
   * Contrasena: `admin123`
3. Explorar los controladores expuestos (`product-controller`, `inventory-controller`, `sale-controller`, `report-controller`).

### Punto 5: Crear una Venta contra la Base de Datos del Contenedor (Create a sale against container DB)
Para validar la persistencia real de extremo a extremo, se procesa una transaccion comercial en el POS contra el entorno contenerizado.

Peticion HTTP:
```bash
curl -i -X POST http://localhost:8080/api/v1/sales \
  -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      { "productId": 1, "quantity": 10 },
      { "productId": 3, "quantity": 5 }
    ]
  }'
```

Calculo esperado segun datos semilla:
* Producto 1 (APL-001): 10 unidades a 2.99 = 29.90. Stock disminuye de 120 a 110.
* Producto 3 (BRD-001): 5 unidades a 3.49 = 17.45. Stock disminuye de 30 a 25.
* Total General: 29.90 + 17.45 = 47.35.

Respuesta esperada (HTTP 201 Created):
```json
{
  "id": 1,
  "saleDate": "2026-09-30T00:30:00Z",
  "totalAmount": 47.35,
  "details": [
    {
      "productId": 1,
      "sku": "APL-001",
      "productName": "Red Apples",
      "quantity": 10,
      "unitPrice": 2.99,
      "subtotal": 29.90
    },
    {
      "productId": 3,
      "sku": "BRD-001",
      "productName": "Sourdough Bread",
      "quantity": 5,
      "unitPrice": 3.49,
      "subtotal": 17.45
    }
  ]
}
```

Verificacion de efectos secundarios en el contenedor:
1. Consultar el nuevo stock:
   `GET http://localhost:8080/api/v1/products/1` -> currentStock = 110.
2. Consultar el movimiento de auditoria registrado:
   `GET http://localhost:8080/api/v1/inventory/movements/1` -> Incluye movimiento `SALE` con cantidad 10 y usuario `pos-terminal`.

---

## Comandos Utiles para Operacion en Contenedores

1. Levantar y compilar en primer plano (ver todos los logs del arranque):
   ```powershell
   docker compose up --build
   ```

2. Consultar el estado de los contenedores:
   ```powershell
   docker compose ps
   ```

3. Visualizar logs especificos del microservicio:
   ```powershell
   docker compose logs -f inventory-service
   ```

4. Detener todos los servicios preservando los datos:
   ```powershell
   docker compose down
   ```

5. Detener todos los servicios y reiniciar la base de datos desde cero (destruir volumenes):
   ```powershell
   docker compose down -v
   ```
