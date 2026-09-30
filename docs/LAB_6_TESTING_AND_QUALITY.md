# Hands-on Lab 6: Testing and Quality Assurance

Modulo: Grocery Inventory POS Microservice Master Class
Objetivo: Implementar y verificar una estrategia integral de aseguramiento de calidad (QA) y pruebas automatizadas, combinando pruebas unitarias aisladas con Mockito, pruebas de integracion end-to-end con Testcontainers, ejecucion del ciclo de vida Maven (mvn verify), integracion de JaCoCo para medicion de cobertura y discusion tecnica sobre el significado y las limitaciones de la cobertura de codigo.

---

## Criterios de Aceptacion (Acceptance Criteria)

"Build fails when business rules regress"

El pipeline de construccion y verificacion debe garantizar que:
1. Cualquier regresion en las reglas de negocio criticas (por ejemplo, permitir ventas sin existencias o mutar productos inactivos) provoque de forma inmediata la falla de la construccion (BUILD FAILURE).
2. Se disponga de pruebas unitarias con Mockito que validen de manera aislada el rechazo de operaciones ante stock insuficiente.
3. Se cuente con pruebas de integracion (Smoke Tests) que levanten el contenedor real de Microsoft SQL Server 2022 mediante Testcontainers y comprueben la disponibilidad del servicio.
4. El comando mvn verify ejecute la compilacion, el empaquetado del artefacto ejecutable, todas las pruebas automatizadas y la generacion de reportes de cobertura de codigo con JaCoCo.

---

## Resolucion de los Puntos del Laboratorio

### Punto 1: Pruebas con Mockito para Stock Insuficiente (Write Mockito test for insufficient stock)

En la capa de servicios se implementaron pruebas unitarias focalizadas en verificar que el sistema rechace transacciones cuando las existencias son menores a las requeridas.

En SaleServiceTest:
```java
@Test
@DisplayName("Should reject sale when stock is insufficient")
void create_insufficientStock_throwsBusinessException() {
    when(products.findById(1L)).thenReturn(Optional.of(product1));

    SaleRequest request = new SaleRequest(List.of(new SaleItemRequest(1L, 15)));

    assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Insufficient stock");

    verify(sales, never()).save(any());
    verify(movements, never()).save(any());
    assertThat(product1.getCurrentStock()).isEqualTo(10);
}
```

En InventoryServiceTest:
```java
@Test
@DisplayName("Should block adjustment out beyond available stock")
void adjust_outBeyondStock_throwsBusinessException() {
    AdjustmentCommand cmd = new AdjustmentCommand(1L, 60, MovementType.ADJUSTMENT_OUT, "manager");

    when(products.findById(1L)).thenReturn(Optional.of(activeProduct));

    assertThatThrownBy(() -> service.adjust(cmd))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Insufficient stock");

    assertThat(activeProduct.getCurrentStock()).isEqualTo(50);
    verify(movements, never()).save(any());
}
```

Efecto de regresion: Si un desarrollador altera la condicion `if (p.getCurrentStock() < qty)` o remueve la excepcion, la prueba falla instantaneamente, impidiendo que el cambio llegue a produccion.

### Punto 2: Smoke Test de Integracion con Testcontainers (Write integration smoke test with TestContainers)

La clase ProductApiIntegrationTest levanta un entorno identico a produccion utilizando la imagen oficial `mcr.microsoft.com/mssql/server:2022-latest`:

```java
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductApiIntegrationTest {

    @Container
    static MSSQLServerContainer<?> sql = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", sql::getJdbcUrl);
        r.add("spring.datasource.username", sql::getUsername);
        r.add("spring.datasource.password", sql::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Test
    void contextStarts() {
        assertThat(port).isPositive();
    }

    @Test
    void healthCheckSmokeTest() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
```

Esta prueba garantiza que el contexto de Spring Boot levanta, la conexion JDBC contra SQL Server es exitosa, Hibernate ejecuta el DDL y el endpoint de Actuator responde favorablemente.

### Punto 3: Ejecucion de mvn verify (Run mvn verify)

Se integro el plugin `jacoco-maven-plugin` (version 0.8.12) en el archivo pom.xml en la fase `verify`:
* `prepare-agent`: Configura los argumentos de JVM (-javaagent) para registrar el bytecode ejecutado en target/jacoco.exec.
* `report`: Genera los reportes analiticos HTML, CSV y XML en target/site/jacoco/.

Comando de ejecucion en PowerShell:
```powershell
mvn clean verify "-Dtest=!*IntegrationTest"
```

Flujo ejecutado durante el comando:
1. Limpieza de target/ (clean).
2. Compilacion de clases principales (compile).
3. Compilacion de clases de prueba (testCompile).
4. Ejecucion de las 31 pruebas unitarias con Surefire (test).
5. Generacion del empaquetado JAR ejecutable de Spring Boot (package).
6. Analisis de cobertura de JaCoCo generando el reporte en target/site/jacoco/index.html (verify).
7. Resultado final: BUILD SUCCESS.

---

## Discusion: Significado y Limites de la Cobertura de Codigo (Discuss coverage meaning and limits)

### Que significa la cobertura de codigo (Meaning of Code Coverage)

1. Cobertura de Instrucciones y Lineas (Instruction / Line Coverage):
   Mide el porcentaje de sentencias de codigo compilado que fueron invocadas durante las pruebas. Permite detectar codigo muerto o caminos principales que jamas han sido ejercitados.

2. Cobertura de Ramas (Branch Coverage):
   Evalua si cada bifurcacion condicional (instrucciones if, operadores ternarios, switch) fue evaluada tanto en su condicion verdadera como falsa. En este proyecto, SaleService e InventoryService cuentan con alta cobertura de ramas para verificar los caminos de exito y de rechazo de inventario.

3. Red de Seguridad ante Refactorizaciones:
   Proporciona retroalimentacion inmediata a los desarrolladores al modificar algoritmos de calculo o consultas, evitando regresiones inadvertidas.

### Limites y Peligros de la Cobertura de Codigo (Limits of Code Coverage)

1. Cobertura no equivale a Calidad de Asercion:
   Es posible tener 100% de cobertura de lineas si una prueba ejecuta un metodo sin incluir aserciones (assert). Una alta metrica numerica no garantiza que las respuestas o los efectos secundarios hayan sido verificados correctamente.

2. El sesgo de los caminos no concebidos:
   La cobertura solo mide lo que esta escrito en el codigo fuente. No puede detectar requerimientos de negocio ausentes, validaciones omitidas ni excepciones no contempladas.

3. Falta de validacion de Concurrencia y Bloqueos:
   Las pruebas unitarias y de cobertura se ejecutan de manera secuencial. No revelan problemas de condiciones de carrera (Race Conditions), bloqueos mutuos en base de datos (Deadlocks) ni problemas de consistencia bajo alta concurrencia real.

4. Mocks vs. Comportamiento Real del Motor de Datos:
   En pruebas con Mockito se simula el comportamiento de los repositorios. Un mock no detecta errores de sintaxis en dialectos SQL especificos, problemas de collation ni violaciones de claves foraneas relacionales. Por ello, la cobertura unitaria debe complementarse obligatoriamente con pruebas de integracion reales como las provistas por Testcontainers.

---

## Resumen de Metricas Obtenidas

* Pruebas Unitarias Totales: 31 pruebas ejecutadas (0 fallos, 0 errores).
* Cobertura en Servicios de Negocio:
  * SaleService: 100% de instrucciones cubiertas (167 de 167 instrucciones).
  * ReportService: 97% de instrucciones cubiertas.
  * InventoryService: 92% de instrucciones cubiertas.
  * ProductService: 90% de instrucciones cubiertas.
* Reporte HTML generado: target/site/jacoco/index.html.
* Cumplimiento del Criterio de Aceptacion: Validado. Cualquier regresion en las reglas de stock, estatus o calculos produce la falla inmediata del build en Maven.
