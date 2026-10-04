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

`check` ejecuta tests, ktlint y detekt, y genera el reporte JaCoCo. Gradle resuelve la convención y la biblioteca PrintScript desde GitHub Packages mediante `GITHUB_ACTOR` y `GITHUB_TOKEN` con acceso de lectura.

Para probar cambios de `gradle-conventions` sin publicarlos, con ese repositorio clonado al lado de este:

```powershell
.\gradlew.bat check --include-build ..\gradle-conventions
```

## Git hooks

Una vez por clon:

```powershell
.\gradlew.bat installGitHooks
```

- `pre-commit`: formatea con ktlint los archivos Kotlin en stage, los vuelve a agregar al commit y corre detekt.
- `pre-push`: corre `check` completo, con los tests.

El detalle está en el README de `gradle-conventions`.