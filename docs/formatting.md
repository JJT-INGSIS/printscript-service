# Formato

PrintScript 1.0 y 1.1 se formatean con la biblioteca del servicio.
Snippets guarda las reglas del usuario y coordina el reprocesamiento.


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
