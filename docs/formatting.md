# SNI-59 — Formato con el formatter real de PrintScript

El servicio formatea código PrintScript 1.0 y 1.1 con el formatter de la
biblioteca `printscript-v1:1.2.0`, y publica qué reglas acepta cada versión.
Es una API interna: Language la adapta y Snippets decide quién puede usarla.
No guarda configuraciones por usuario ni reprocesa snippets.

## API

| Ruta | Respuesta |
| --- | --- |
| `GET /formatting/rules?version=1.1` | `200` con el catálogo de esa versión |
| `POST /formatting/rules/check` con `{"version","rules"}` | `200` `{"valid","violations"}` |
| `POST /formatting` con `{"code","version","rules"}` | `200` `{"formatted","code","diagnostics"}` |

`rules` es un objeto JSON `id → valor`. Una regla que no se manda queda apagada,
y `null` apaga una regla numérica.

Cada regla del catálogo trae `id`, `type` (`boolean` o `integer`), `default`,
`minimum`, `maximum`, `conflictsWith` y `description`. Las numéricas van de `0`
al tope configurado (`10` por defecto).

### Resultados

| Caso | Respuesta |
| --- | --- |
| Código formateado | `200` `{"formatted":true,"code":"…","diagnostics":[]}` |
| Error léxico (por ejemplo un texto sin cerrar) | `200` `{"formatted":false,"code":null,"diagnostics":[{rule,message,line,column}]}` |
| Reglas inválidas | `422` `urn:printscript:problem:invalid-formatting-rules` con `violations` |
| Código de más de 1 MiB (UTF-8) | `400` `urn:printscript:problem:request-too-large` |
| Resultado de más de 2 MiB | `422` `urn:printscript:problem:formatted-output-too-large` (no se corta) |
| Versión no soportada | `422` con `supportedVersions`, igual que en validar |
| Body mal formado o sin `rules` | `400` |

Motivos de `violations`:

| Motivo | Cuándo |
| --- | --- |
| `unknown-rule` | La regla no existe en ninguna versión |
| `unavailable-in-version` | Existe, pero en otra versión (por ejemplo `indent-inside-if` en 1.0) |
| `wrong-type` | El valor no es del tipo de la regla. Se toma tal como está escrito: `"true"`, `"1"` o `1e2` son `wrong-type` |
| `out-of-range` | Número negativo o mayor que el tope |
| `conflicting-rules` | Dos reglas excluyentes prendidas; se informan las dos |

Todas las violaciones se informan juntas. Con reglas inválidas no se formatea.

## Qué hace la biblioteca y qué hace el servicio

| Pieza | Dónde |
| --- | --- |
| Lista de reglas por versión, tipos y conflictos | Biblioteca: `rules()` |
| Validación por regla con motivo tipado | Biblioteca: `check(json)` |
| Formatear | Biblioteca: `fromJson` + `PrintScriptV1(1)FormatterFactory` |
| Descripción en castellano de cada regla | Servicio: `FormattingRuleDescriptions` |
| `unavailable-in-version` y el tope de las numéricas | Servicio: `FormattingRuleCheck` |
| Límites de tamaño de código y de resultado | Servicio: `FormattingLimits` y `FormattedText` |

El formatter trabaja solo con tokens: no pasa por el parser. Por eso un código
con errores de sintaxis o de tipos se formatea igual, y solo un error léxico
lo impide.

En 1.1, `if-brace-below-line` baja la llave a la línea siguiente e
`indent-inside-if` sangra las líneas que están dentro del `if`. Si el `if` está
todo en una línea (`if(true){println(1);}`), el contenido queda pegado salvo
que se prenda `mandatory-line-breaks-inside-if`.

## Límites

| Propiedad | Variable | Default |
| --- | --- | --- |
| `formatting.code-bytes` | `FORMATTING_CODE_BYTES` | `1048576` |
| `formatting.output-bytes` | `FORMATTING_OUTPUT_BYTES` | `2097152` |
| `formatting.maximum-count` | `FORMATTING_MAXIMUM_COUNT` | `10` |

Los tres forman parte de la revisión `format` del procesador: cambiar un límite
cambia esa revisión, pero no las de validar ni ejecutar. Toda respuesta de
`/formatting/**` trae el header `Processor-Revision` con esa revisión, y
`GET /processor` la lista en `capabilities.format`
(ver [identidad del procesador](processor-identity.md)).

## Diseño

| Pieza | Qué hace |
| --- | --- |
| `FormattingController`, `FormattingRequests`, `FormattingResponses` | Rutas, bodies y traducción a HTTP |
| `FormattingService` | Valida las reglas, controla el tamaño y formatea |
| `PrintScriptFormatters` | Único lugar que conoce las clases de la biblioteca de cada versión |
| `FormattingRuleCheck` | Traduce los motivos de la biblioteca y aplica el tope |
| `FormattingRuleDescriptions` | Texto en castellano de cada regla |
| `FormattedText` | Junta el resultado y corta si pasa el límite |
| `FormattingLimits` | Límites configurables |

## Decisiones

Aprobadas por Juan el 31/10/2026 («dale vamos con esas 6») y ajustadas el
01/11/2026, cuando el equipo aprobó publicar la 1.2.0 («aprobadas las 6»).

| # | Decisión | Alternativa descartada | Costo aceptado |
| --- | --- | --- | --- |
| 1 | El catálogo y la validación por regla vienen de la biblioteca 1.2.0 (#121) | Lista en el servicio con un test por reflexión contra la biblioteca | Hubo que publicar una versión nueva de la biblioteca |
| 2 | Tres rutas: catálogo, chequeo y formato | Una sola ruta que formatea y valida | — |
| 3 | No validar sintaxis antes de formatear | Pasar primero por el parser | Se formatea código que no compila |
| 4 | Límites de 1 MiB de código, 2 MiB de resultado y números hasta 10 | Sin límites (la biblioteca acepta hasta `Int.MAX_VALUE`) | Configs grandes se rechazan |
| 5 | Language usa una estrategia de formateo con resultados tipados | Language llama a HTTP directo | — |
| 6 | Revisión `format` = artefacto + límites, en todo `/formatting/**` | Reusar la revisión de validar | Una capacidad más en `/processor` |

La 1.2.0 trae también el refactor del formatter (#120): agrega
`mandatory-line-breaks-inside-if` en 1.1 y corrige `if-brace-below-line`.
Validar y ejecutar no cambian.

## Verificación

`./gradlew build`:

- `FormattingServiceTest`: cada regla, Unicode y CRLF, errores léxicos, motivos de las violaciones, tope, límites en bytes UTF-8.
- `FormattingHttpTest`: catálogo, versión no soportada, chequeo, formato, Problem Details, body mal formado.
- `FormattingRuleDescriptionsTest`: toda regla de la biblioteca tiene descripción.
- `ProcessorIdentityTest` y `ProcessorRevisionHttpTest`: revisión `format` y header en `/formatting/**`.

### Evidencia con Compose — 01/11/2026

Imagen construida desde infra con
`docker compose --profile ui --profile ingress up -d --build printscript-service snippets-service`:

| Comprobación | Resultado |
| --- | --- |
| `sha256sum /app/app.jar` dentro de la imagen | `7119dd89…ecd2`, igual a `artifact` de `GET /processor` |
| `capabilities.format` en `GET /processor` | `sha256-v1:2001166d…5289` |
| `GET /formatting/rules?version=1.1` | `200`, 12 reglas (incluye `mandatory-line-breaks-inside-if`), header = `capabilities.format` |
| `POST /formatting` de `if(true){println(1);}` con llave abajo, saltos dentro del `if` y sangría 2 | `200`, `"if(true)\n{\n  println(1);\n}"`, header = `capabilities.format` |
| Reglas inválidas en 1.0 (`indent-inside-if`, `"true"`, `11`) | `422` `invalid-formatting-rules` con `unavailable-in-version`, `wrong-type` y `out-of-range` |
| Texto sin cerrar | `200`, `formatted:false`, `UNTERMINATED_STRING` en 1:9 |
| Versión `9.9` | `422` con `supportedVersions` |
| `POST /validate` | Sin cambios: válido, con la revisión de validar |
| Aceptación SNI-53 (`scripts/verify_us8_10.py`) | 15/15: la 1.2.0 no cambió validar ni ejecutar |
