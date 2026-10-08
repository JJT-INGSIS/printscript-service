# PrintScript Service

Servicio HTTP de lenguaje de Snippet Searcher. Recibe código PrintScript y lo procesa con la biblioteca publicada `printscript-v1:1.1.0`. No guarda snippets ni tiene base de datos.

## Operaciones

| Operación | Endpoint | Contrato |
| --- | --- | --- |
| Validar código de PrintScript 1.0 o 1.1 | `POST /validate` | [docs/validation.md](./docs/validation.md) |

El contrato documenta el pedido, las respuestas, los errores, el alcance de la validación y el catálogo de reglas. Sus ejemplos se ejecutan como tests en cada build.

Para probarlo en la computadora, con el servicio levantado mediante `bootRun`:

```bash
curl -s -X POST http://localhost:8080/validate -H "Content-Type: application/json" -d '{"code": "println(1);", "version": "1.0"}'
```

## Estructura

| Paquete | Responsabilidad |
| --- | --- |
| `validation` | El endpoint: recibe el pedido, valida y arma la respuesta. |
| `language` | Las versiones soportadas y el lexer, parser y validador de cada una. |
| `diagnostic` | Traduce cada error de la biblioteca a regla, mensaje, línea y columna. |
| `web` | Convierte las excepciones en respuestas de error con formato Problem Details. |

## Build

Usa `jjt.spring-service:0.2.0` de `gradle-conventions`, JDK 21 y el wrapper Gradle 9.3.0.

```powershell
.\gradlew.bat check
.\gradlew.bat bootRun
```

En macOS y Linux, `./gradlew check` y `./gradlew bootRun`.

`check` ejecuta tests, ktlint y detekt, y genera el reporte JaCoCo. Gradle resuelve la convención y la biblioteca PrintScript desde GitHub Packages mediante `GITHUB_ACTOR` y `GITHUB_TOKEN` con acceso de lectura.

Para probar cambios de `gradle-conventions` sin publicarlos, con ese repositorio clonado al lado de este:

```powershell
.\gradlew.bat check --include-build ..\gradle-conventions
```

`.gitattributes` fija los saltos de línea: `gradlew`, los scripts de shell y los archivos Kotlin siempre se obtienen con LF, y los `.bat` con CRLF. `gradlew` tiene permiso de ejecución en el repositorio. Quien tenga un clon anterior a este cambio en Windows debe actualizar `main` y volver a obtener `gradlew` con `git checkout -- gradlew`.

## Docker

Requiere Docker con BuildKit. No hace falta tener Java ni Gradle instalados para construir la imagen.

### Construir la imagen

Con `GITHUB_ACTOR` y `GITHUB_TOKEN` definidas en la terminal, desde esta carpeta:

```bash
docker build --secret id=github_actor,env=GITHUB_ACTOR --secret id=github_token,env=GITHUB_TOKEN -t printscript-service:local .
```

El `Dockerfile` tiene dos etapas. La primera genera `build/libs/app.jar` con JDK 21 y el wrapper del proyecto. La segunda parte de una imagen con JRE 21 y copia únicamente ese jar.

Las credenciales se entregan como secretos de BuildKit, con los mismos identificadores que usa el Compose de [snippet-searcher-infra](https://github.com/JJT-INGSIS/snippet-searcher-infra): `github_actor` y `github_token`. Se montan solo mientras corre el comando de Gradle y no se guardan en ninguna capa. No se usan `ARG` ni `ENV` para pasarlas.

Si falta un secreto, el build se detiene con `secret github_actor: not found`. Docker reutiliza las capas ya construidas: si los archivos no cambiaron, el paso de Gradle no se vuelve a ejecutar y no pide los secretos. Para forzarlo, agregar `--no-cache`.

La imagen solo empaqueta la aplicación. Los tests, ktlint y detekt se ejecutan con `check` y en CI.

### Arrancar el contenedor

```bash
docker run -d --rm --name printscript-service -p 127.0.0.1:8082:8080 printscript-service:local
```

La aplicación escucha en el puerto `8080` del contenedor y corre con el usuario sin privilegios `10001`. El ejemplo la publica en el `8082` de la computadora porque el `8080` y el `8081` los usan los otros servicios del Compose. No necesita base de datos ni variables de entorno.

### Comprobar

```bash
curl -s -X POST http://localhost:8082/validate -H "Content-Type: application/json" -d '{"code": "println(1);", "version": "1.0"}'
docker history --no-trunc printscript-service:local | grep -c GITHUB_TOKEN
docker stop printscript-service
```

El primer comando debe responder `{"valid":true,"diagnostics":[]}`. El segundo debe dar `0`: las credenciales no aparecen en el historial de la imagen.

El Compose del proyecto vive en `snippet-searcher-infra`; este repositorio no incorpora otro.

## Git hooks

Una vez por clon:

```powershell
.\gradlew.bat installGitHooks
```

- `pre-commit`: formatea con ktlint los archivos Kotlin en stage, los vuelve a agregar al commit y corre detekt.
- `pre-push`: corre `check` completo, con los tests.

El detalle está en el README de `gradle-conventions`.

## CI, publicación y branches — SNI-23

Crear ramas por cambio desde `dev` y abrir PR hacia dev. Promover mediante PR `dev` → `main`, con `dev` como default branch. Usar squash para cambios individuales y merge commit para promociones. Proteger ambas ramas con PR, CI requerido y actualización con la base, sin aprobación humana obligatoria.

El caller `.github/workflows/pipeline.yml` define cuándo ejecutar y usa `kotlin-service-pipeline.yml@v0.3.1` de [github-workflows](https://github.com/JJT-INGSIS/github-workflows). El tag debe existir antes de integrar los callers definitivos; la validación del candidato usa su SHA como explica el README central.

| Evento | Resultado |
| --- | --- |
| PR a dev/main | CI con `build`; no publica |
| Push a dev | CI → publicar únicamente la imagen de PrintScript |
| Push a main | CI; promoción a prod pendiente de SNI-25 |
| Ejecución manual en dev/main | CI; activar `publish` para publicar bootstrap |

La ejecución manual requiere el caller en la default branch, actualmente `dev`. Se conservan los IDs del check `verify / verify / build`; elegir el nombre real de Actions en las reglas de branches. Un CI fallido o cancelado no habilita publicación.

Paquete de aplicación: `ghcr.io/jjt-ingsis/printscript-service`. Es una imagen del servicio HTTP, distinta del artefacto Maven `printscript-v1`. El resumen registra el SHA, Git tree, plataformas y digest. Usar `image-ref` (`imagen@sha256:...`) para desplegar; los tags son `sha-<SHA completo>` y `run-<run_id>-<run_attempt>`. Esta aplicación no necesita PostgreSQL.

Los secrets `GH_PACKAGES_USER` y `GH_PACKAGES_READ_TOKEN` deben permitir descargar la convención y la biblioteca PrintScript. Preferir su configuración de organización con acceso a este repo. Se utilizan en Gradle y como secretos BuildKit; la publicación a GHCR usa el `GITHUB_TOKEN` automático con `packages: write`.

Configurar la variable de repositorio `DOCKER_PLATFORMS` cuando se acuerde la arquitectura de las VMs con Thiago; el fallback es `linux/amd64`. También se admiten `linux/arm64` y `linux/amd64,linux/arm64`.

Preparar GitHub Environments `dev` (solo branch dev) y `prod` (solo main). SNI-25 resolverá variables `SSH_HOST`, `SSH_USER`, `SSH_PORT` y secrets `SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS` del ambiente elegido. Los valores reales y SSH se verifican cuando estén las VMs y el stack.

Ver contrato reusable, outputs, permisos, visibilidad y secuencia de integración en el [README central](https://github.com/JJT-INGSIS/github-workflows/blob/main/README.md). Las primeras publicaciones y comprobaciones completas quedan pendientes de integrar/publicar esa versión.
