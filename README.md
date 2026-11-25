# Servicio de PrintScript

Servicio HTTP interno de Snippet Searcher. Valida, formatea, analiza y ejecuta código PrintScript 1.0 y 1.1 con la biblioteca `printscript-v1:1.2.0`. Snippets autoriza las operaciones de usuario y guarda los snippets. PrintScript usa Redis para conservar el estado y los resultados de las operaciones durables de lint y formato.

## API interna

| Operación | Rutas | Contrato |
| --- | --- | --- |
| Validar código | `POST /validate` | [Validación](docs/validation.md) |
| Formatear y consultar o validar reglas | `POST /formatting`, `GET /formatting/rules`, `POST /formatting/rules/check` | [Formato](docs/formatting.md) |
| Analizar con lint y consultar o validar reglas | `POST /linting`, `GET /linting/rules`, `POST /linting/rules/check` | [Lint](docs/linting.md) |
| Iniciar una sesión de ejecución, consultar estado, enviar inputs, leer eventos NDJSON y cancelar | `POST /executions`, `GET /executions/{id}`, `POST /executions/{id}/inputs`, `GET /executions/{id}/events`, `POST /executions/{id}/cancel` | [Ejecución](docs/execution.md) |
| Iniciar o recuperar, consultar y cancelar operaciones durables | `PUT /operations/{id}`, `GET /operations/{id}`, `POST /operations/{id}/cancel` | [Operaciones durables](docs/execution.md#operaciones-durables) |
| Consultar revisiones y capacidades del procesador | `GET /processor` | [Identidad del procesador](docs/processor-identity.md) |

Las operaciones durables están deshabilitadas por defecto. Para habilitarlas se requiere Redis, `PRINTSCRIPT_OPERATIONS_ENABLED=true` y `PRINTSCRIPT_OPERATIONS_DEPLOYMENT_MODE=single-stop-first`. Usá la configuración del [stack de infraestructura](https://github.com/JJT-INGSIS/snippet-searcher-infra) para el despliegue.

## Desarrollo local

Usá JDK 21 y el wrapper de Gradle 9.3.0. El servicio aplica `jjt.spring-service:0.3.0`. Para resolver la convención y la biblioteca PrintScript desde GitHub Packages, definí `GITHUB_ACTOR` con el usuario de GitHub y `GITHUB_TOKEN` con un token clásico con permiso `read:packages` y acceso a los paquetes del proyecto.

```powershell
.\gradlew.bat check
.\gradlew.bat bootRun
```

En Linux y macOS usá `./gradlew`. `check` ejecuta tests, ktlint, Detekt y el reporte JaCoCo. Para ejecutar solo los tests, usá la tarea `test`. La suite de operaciones durables necesita Docker para levantar Redis con Testcontainers. Los tests de contrato leen [`docs/validation.md`](docs/validation.md).

Con el servicio levantado, podés probar la validación en el puerto `8080`:

```bash
curl -s http://localhost:8080/validate -H 'Content-Type: application/json' -d '{"code":"println(1);","version":"1.0"}'
```

## Imagen Docker

Con las mismas variables de GitHub Packages y BuildKit habilitado:

```bash
docker build --secret id=github_actor,env=GITHUB_ACTOR --secret id=github_token,env=GITHUB_TOKEN -t printscript-service:local .
docker run --rm -p 127.0.0.1:8082:8080 printscript-service:local
```

El [Dockerfile](Dockerfile) genera `app.jar` con Temurin JDK 21 y copia el JAR y los archivos del agente de New Relic a la imagen Temurin JRE 21. El contenedor escucha en `8080`, corre como usuario `10001` y arranca con `start-service.sh`. New Relic se activa en runtime con `NEW_RELIC_ENABLED=true` y se configura mediante el stack. Las credenciales de GitHub Packages se montan como secretos de BuildKit durante la compilación.

## CI/CD

El [pipeline](.github/workflows/pipeline.yml) llama a `kotlin-service-pipeline.yml@v0.6.8`. Las PRs a `dev` y `main` ejecutan CI. Los pushes a `dev` publican en `ghcr.io/jjt-ingsis/printscript-service` después de CI y despliegan en DEV si `CD_ENABLED=true`. En una ejecución manual desde `dev`, `publish` permite pedir la publicación aun con el despliegue deshabilitado.

En `main`, habilitar el despliegue requiere `CD_ENABLED=true` y `PROD_DEPLOY_ENABLED=true`. La promoción usa el digest de una imagen verificada en DEV para el mismo árbol Git. Consultá el [manual de operación y recuperación](https://github.com/JJT-INGSIS/snippet-searcher-infra/blob/dev/docs/sni-25-cd.md) para la configuración, la evidencia de promoción y el rollback.
