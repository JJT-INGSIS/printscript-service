# SNI-76 — Identidad del procesador

La versión de lenguaje (`1.0` / `1.1`) dice **qué reglas** se aplican. La revisión
del procesador dice **qué build exacto** de este servicio las aplicó. Dos builds
distintos pueden soportar "1.1" y dar resultados distintos, por ejemplo si uno
tiene un bug arreglado. Por eso cada respuesta dice qué build la produjo.

Base: [ADR-001 y diseño asíncrono](https://github.com/JJT-INGSIS/snippet-searcher-infra/blob/dev/docs/architecture/async-processing-design.md#snapshots-selección-y-vigencia).
La identidad no se deduce de `1.0`/`1.1` y no cambia los bodies ni el NDJSON.

## Qué identifica

| Dato | Cómo se calcula |
| --- | --- |
| Artefacto | SHA-256 del `app.jar` que está corriendo: código del servicio, biblioteca `printscript-v1` y defaults. Se lee del único jar del classpath (`java -jar /app/app.jar`) |
| Revisión de **validar** | `sha256-v1:` + SHA-256 de `validate\|<artefacto>\|` |
| Revisión de **ejecutar** | `sha256-v1:` + SHA-256 de `execute\|<artefacto>\|<límites de ejecución efectivos>` |

Cada capacidad tiene su propia revisión: cambiar un límite de ejecución
(`EXECUTION_TOTAL_SECONDS`, por ejemplo) cambia la revisión de ejecutar, pero no
la de validar. Así no parece que cambió el parser.

**Fuera de un jar no hay identidad.** Corriendo desde el IDE o en los tests, el
classpath tiene varias carpetas: el servicio responde igual, pero **sin** header.
Nunca se inventa una revisión.

**Cada build es una revisión nueva.** El jar guarda fechas de compilación, así que
volver a compilar el mismo código da otro artefacto y otra revisión. La identidad
es del artefacto que corre, no del código fuente.

## Contrato

| Elemento | Detalle |
| --- | --- |
| Header `Processor-Revision` | En **toda** respuesta de `POST /validate` (revisión de validar) y de `/executions/**`: inicio, consulta, eventos, input y cancelación (revisión de ejecutar). También en errores como `422` o `404`. Formato `sha256-v1:<64 hex>` |
| `GET /processor` | Página interna: `{"artifact":"sha256:<hex>","capabilities":{"validate":"sha256-v1:…","execute":"sha256-v1:…"}}`. Sin jar, los valores son `null` |

Lo que acredita una operación es **el header de su propia respuesta**. `GET /processor`
sirve para ver qué revisión está disponible (por ejemplo, antes de que SNI-70
active un parser), pero no prueba quién ejecutó: durante un reemplazo, la consulta
la puede contestar un build y la operación hacerla otro.

Formato y lint (SNI-59/60) usan el mismo header y el mismo formato. Su revisión es
el artefacto más la configuración que afecte a esa capacidad, y se agrega a
`ProcessorIdentity.revisionFor` y a `capabilities`.

## Cómo verificarlo en una imagen

```sh
docker run --rm --entrypoint sha256sum ghcr.io/jjt-ingsis/printscript-service@sha256:<digest> /app/app.jar
```

El hash tiene que coincidir con `artifact` de `GET /processor` del contenedor que
corre esa imagen, y con el header de cada respuesta.

Publicar o desplegar una imagen **no** activa un parser ni genera
`ParserVersionActivated`. La activación es explícita y administrativa (SNI-70).

## Diseño

| Pieza | Qué hace |
| --- | --- |
| `ProcessorArtifact` / `RunningJar` | Calcula el SHA-256 del único jar del classpath; si no hay uno, `null` |
| `ProcessorIdentity` | Revisión de cada capacidad y página `/processor` |
| `ProcessorRevisionFilter` | Agrega el header según la ruta, antes de que responda el controller |
| `ProcessorConfiguration` | Arma esas piezas y registra el filtro |
| `ProcessorController` | `GET /processor` |

## Decisiones

Aprobadas por Juan el 30/10/2026 («dale vamos con esas 5 recomendaciones»).

| # | Decisión | Alternativa descartada | Costo aceptado |
| --- | --- | --- | --- |
| 1 | Hash del jar que corre | Manifiesto de build con commit; digest de imagen por variable | Sin jar no hay identidad; cada build es una revisión |
| 2 | Una revisión por capacidad | Solo el jar; jar + toda la configuración | Hay que agregar la revisión de cada capacidad nueva |
| 3 | Header en cada respuesta + `GET /processor` | Campo nuevo en los bodies | El cliente tiene que leer headers |
| 4 | Snippets registra y clasifica; exigir queda para SNI-70/59/60/69 | Rechazar ya si falta | Por ahora no bloquea crear ni ejecutar |
| 5 | Este documento como contrato | Documento en infra | — |

## Verificación

`./gradlew check`: `ProcessorIdentityTest` (mismas entradas → misma revisión,
límites → solo cambia ejecutar, otro artefacto → cambian todas, sin jar → sin
revisión, SHA-256 del jar), `ProcessorRevisionHttpTest` (header en validar,
ejecutar, eventos y errores; página `/processor`) y `MissingProcessorRevisionTest`
(sin jar, sin header).

Con el jar real, el 30/10/2026, `GET /processor` publicó el mismo SHA-256 que
`shasum -a 256 build/libs/app.jar`.
