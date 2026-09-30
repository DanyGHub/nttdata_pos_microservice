# Hands-on Lab 8: CI/CD and Docker Hub

Modulo: Grocery Inventory POS Microservice Master Class
Objetivo: Implementar y documentar un pipeline automatizado de Integracion y Despliegue Continuo (CI/CD) mediante GitHub Actions, configurando secretos seguros de repositorio, ejecucion automatica de pruebas y verificacion en Pull Requests, empaquetado de imagenes Docker con etiquetado semantico trazable y publicacion automatica hacia el registro publico Docker Hub al fusionar cambios en la rama principal.

---

## Criterios de Aceptacion (Acceptance Criteria)

"Pipeline produces a traceable container artifact."

El pipeline de CI/CD debe asegurar que:
1. Las credenciales de acceso al registro Docker Hub esten desacopladas del codigo fuente mediante el uso de Secretos de Repositorio (Repository Secrets).
2. Todo Pull Request dirigido a la rama main active automaticamente el flujo de compilacion, ejecucion de pruebas y empaquetado Docker sin publicar artefactos al registro.
3. El artefacto resultante contenga un etiquetado inmutable y trazable basado en la numeracion de ejecucion de GitHub Actions (1.0.X) y el commit SHA correspondiente.
4. Unicamente los eventos push en la rama main (como un merge completado) autentiquen contra Docker Hub y publiquen las imagenes generadas.

---

## Resolucion de los 5 Puntos del Laboratorio

### Punto 1: Crear Secretos en el Repositorio (Create repository secrets)
Para evitar la exposicion publica de credenciales en los archivos YAML del repositorio, se deben configurar dos secretos en la interfaz de GitHub:

Ruta de configuracion en GitHub:
Settings -> Secrets and variables -> Actions -> New repository secret

Secretos requeridos:
1. `DOCKERHUB_USERNAME`:
   * Descripcion: Nombre de usuario de la cuenta de Docker Hub donde se alojara el repositorio de imagenes.
2. `DOCKERHUB_TOKEN`:
   * Descripcion: Token de acceso personal (Personal Access Token - PAT) generado en Docker Hub (Account Settings -> Security -> New Access Token) con permisos de lectura y escritura (Read & Write).
   * Buena practica: El uso de PAT permite revocar el acceso en cualquier momento sin comprometer la contrasena maestra de la cuenta.

### Punto 2: Ejecutar el Flujo en Pull Requests (Run workflow on pull request)
El flujo de trabajo se activa ante eventos `pull_request` dirigidos a `main`:

```yaml
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
```

Comportamiento en Pull Requests:
* Ejecuta `actions/checkout@v4` y configura Java 21 con distribucion Temurin y cache de Maven.
* Ejecuta `mvn -B clean verify`, corriendo las 31 pruebas unitarias y generando el analisis de cobertura JaCoCo. Si alguna prueba falla, el Pull Request se bloquea y se impide la integracion.
* Ejecuta `docker build` para comprobar que el Dockerfile compila sin errores.
* Los pasos de inicio de sesion (`docker/login-action@v3`) y publicacion (`docker push`) son ignorados gracias a la condicion:
  ```yaml
  if: github.ref == 'refs/heads/main' && github.event_name == 'push'
  ```

### Punto 3: Construir la Imagen Docker (Build Docker image)
Una vez superada la fase de pruebas unitarias y empaquetado de Maven, el agente de CI construye la imagen Docker:

```yaml
- name: Build Docker image
  run: docker build -t inventory-pos-service:${{ github.sha }} .
```

El uso de `${{ github.sha }}` vincula de forma univoca la imagen construida con el identificador criptografico del commit exacto que disparo la construccion.

### Punto 4: Etiquetado de Version Trazable (Tag version)
Para cumplir formalmente con el criterio de aceptacion, se generan dos etiquetas (tags) para el artefacto:

1. Etiqueta Inmutable de Version (Version Tag):
   `${{ secrets.DOCKERHUB_USERNAME }}/inventory-pos-service:1.0.${{ github.run_number }}`
   * Permite trazabilidad total: Cada despliegue tiene un numero unico incremental generado por GitHub Actions (`github.run_number`).
   * Facilita auditorias y procedimientos de marcha atras (Rollback) a versiones especificas.

2. Etiqueta Flotante (Latest Tag):
   `${{ secrets.DOCKERHUB_USERNAME }}/inventory-pos-service:latest`
   * Apunta siempre a la version mas reciente desplegada desde main.

Comando ejecutado por el flujo:
```bash
docker tag inventory-pos-service:${{ github.sha }} ${{ secrets.DOCKERHUB_USERNAME }}/inventory-pos-service:1.0.${{ github.run_number }}
docker tag inventory-pos-service:${{ github.sha }} ${{ secrets.DOCKERHUB_USERNAME }}/inventory-pos-service:latest
```

### Punto 5: Publicar al Registro desde Main (Push to registry from main)
Cuando un cambio es fusionado (merged) a la rama `main`, el pipeline ejecuta la publicacion formal hacia Docker Hub:

```yaml
- name: Log in to Docker Hub
  if: github.ref == 'refs/heads/main' && github.event_name == 'push'
  uses: docker/login-action@v3
  with:
    username: ${{ secrets.DOCKERHUB_USERNAME }}
    password: ${{ secrets.DOCKERHUB_TOKEN }}

- name: Tag version and push to Docker Hub
  if: github.ref == 'refs/heads/main' && github.event_name == 'push'
  run: |
    IMAGE_TAG="${{ secrets.DOCKERHUB_USERNAME }}/inventory-pos-service:1.0.${{ github.run_number }}"
    IMAGE_LATEST="${{ secrets.DOCKERHUB_USERNAME }}/inventory-pos-service:latest"
    
    docker tag inventory-pos-service:${{ github.sha }} "$IMAGE_TAG"
    docker tag inventory-pos-service:${{ github.sha }} "$IMAGE_LATEST"
    
    docker push "$IMAGE_TAG"
    docker push "$IMAGE_LATEST"
```

---

## Ubicacion y Estructura del Archivo de Workflow

GitHub Actions requiere obligatoriamente que los flujos residan dentro de `.github/workflows/` para que su motor los reconozca.

Archivos implementados:
1. `.github/workflows/ci.yml`: Archivo primario operativo leido por GitHub.
2. `ci/github-actions.yml`: Respaldo sincronizado dentro de la estructura de documentacion del proyecto.

Contenido completo del Workflow:
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-test-and-publish:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'maven'

      - name: Compile, test, and verify with Maven
        run: mvn -B clean verify

      - name: Build Docker image
        run: docker build -t inventory-pos-service:${{ github.sha }} .

      - name: Log in to Docker Hub
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Tag version and push to Docker Hub
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        run: |
          IMAGE_TAG="${{ secrets.DOCKERHUB_USERNAME }}/inventory-pos-service:1.0.${{ github.run_number }}"
          IMAGE_LATEST="${{ secrets.DOCKERHUB_USERNAME }}/inventory-pos-service:latest"
          
          docker tag inventory-pos-service:${{ github.sha }} "$IMAGE_TAG"
          docker tag inventory-pos-service:${{ github.sha }} "$IMAGE_LATEST"
          
          docker push "$IMAGE_TAG"
          docker push "$IMAGE_LATEST"
```

---

## Verificacion de Integracion Local

Para simular localmente las operaciones que ejecutara el runner de GitHub Actions:

1. Ejecucion de verificacion completa de Maven (compilacion, pruebas y cobertura):
   ```powershell
   mvn clean verify "-Dtest=!*IntegrationTest"
   ```
   Resultado: 31 pruebas ejecutadas exitosamente, 0 fallos, 0 errores, BUILD SUCCESS.

2. Simulacion de construccion de imagen Docker con SHA local:
   ```powershell
   $commitSha = git rev-parse --short HEAD
   docker build -t "inventory-pos-service:$commitSha" .
   ```

Con esta configuracion, el repositorio cuenta con un ciclo de vida CI/CD seguro, automatizado y completamente trazable.
