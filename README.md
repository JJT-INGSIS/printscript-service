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

## CI/CD y branches — SNI-23 / SNI-25

Crear ramas cortas desde `dev`, abrir PR a dev y promover mediante PR dev → main.
Dev es la default branch; usar squash para cambios individuales y merge commit
para promociones. Main debe exigir PR y CI con base actualizada, aplicar las reglas
a administradores y no admitir bypass de pushes directos. El merge de esa PR es
la autorización humana; no se agrega otro approval en GitHub Environments.

El caller `.github/workflows/pipeline.yml` prepara
`kotlin-service-pipeline.yml@v0.5.1` de [github-workflows](https://github.com/JJT-INGSIS/github-workflows).
Esta versión espera la convergencia de un rollback al digest esperado antes de
declarar la recuperación; una candidata fallida mantiene la Action en rojo.
**Esta versión debe integrarse, publicarse y comprobarse antes de integrar el caller.**
El candidato se prueba por SHA siguiendo la
[guía central](https://github.com/JJT-INGSIS/github-workflows/blob/main/docs/sni-25-integration.md).

| Evento | Resultado |
| --- | --- |
| PR dev/main | `build` completo y agregador requerido; sin publicación ni despliegue |
| Push dev | CI → publicar imagen; con CD habilitado, desplegar y verificar dev |
| Push main | CI → recuperar digest verificado del mismo Git tree → prod, sin reconstruir |
| Manual dev | CI; publicación opcional o incluida por CD habilitado |
| Manual main | CI y reintento de promoción; activar `publish` falla |

CI fallido o cancelado bloquea CD. `Resultado requerido` solo comprueba el estado
de CI y no repite Gradle. Conservar el check `verify / verify / build` durante la
transición; exigir el agregador nuevo cuando aparezca y pase en GitHub.

Imagen pública: `ghcr.io/jjt-ingsis/printscript-service`. Usar siempre `imagen@sha256:...`;
los tags `sha-<SHA completo>` y `run-<run_id>-<run_attempt>` sirven para localizarla.
El resumen y las etiquetas registran origen, Git tree, digest e intento de build.
Prod exige coincidencia exacta de todo el repositorio, incluso README/workflows;
sin evidencia confiable falla. Dev puede haber avanzado al promover.

Secrets `GH_PACKAGES_USER` y `GH_PACKAGES_READ_TOKEN`: lectura de dependencias
Gradle y secretos BuildKit. GHCR usa `GITHUB_TOKEN` automático con `packages: write`;
CD agrega lectura de Actions/PR y `deployments: write`, sin otro PAT para las VMs.
`DOCKER_PLATFORMS` tiene default linux/amd64, compatible con las VMs x64.

En **este repo**, environments dev (solo dev) y prod (solo main): variables
`SSH_HOST`, `SSH_USER`, `SSH_PORT`; secrets `SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS`.
Los resuelve el job final del workflow reutilizable. No son las contraseñas de
runtime ni activan el perfil Spring. Configurar una clave dedicada y host keys
verificadas con Thiago.

Habilitación gradual con variables **de repositorio**:

- Sin `CD_ENABLED=true`, continúa CI/publicación sin despliegue remoto.
- Habilitar dev después del bootstrap, credenciales y revisión fija de infra.
- Configurar `PROD_DEPLOY_ENABLED=true` solo después de verificar las reglas
  completas de main y prod. El 13/10/2026 se activó y se verificó `Do not allow
  bypassing the above settings` en main: PR, CI y base actualizada se exigen también
  a administradores. El despliegue automático a producción sigue deshabilitado.

El despliegue cambia solo esta aplicación y conserva bases y otras imágenes.
Una pérdida SSH se reconcilia con el journal de la VM; rollback mantiene la Action
roja y no revierte migraciones ni datos. Conservar contratos y esquemas compatibles
con la versión anterior. Una VM apagada produce fallo, sin encendido automático.

Ver [operación y recuperación SNI-25](https://github.com/JJT-INGSIS/snippet-searcher-infra/blob/dev/docs/sni-25-cd.md).
La integración por tag, resolución de environment secrets y el recorrido real
dev → prod quedan pendientes de la configuración y pruebas con VMs encendidas.

El paquete de aplicación es distinto del artefacto Maven printscript-v1.
PrintScript no necesita PostgreSQL; el CD no agrega una base a este servicio.
