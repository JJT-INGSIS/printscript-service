# Lint

La API síncrona analiza la selección enviada por el cliente. Los lotes de
Snippets usan [operaciones durables](execution.md#operaciones-durables).


## Contrato interno

| Operación | Entrada | Resultado |
| --- | --- | --- |
| GET /linting/rules?version=1.1 | Versión explícita | Catálogo real por versión |
| POST /linting/rules/check | version, rules | valid y violations |
| POST /linting | version, code, rules | analyzed, compliant, diagnostics |

`rules` es obligatorio y debe ser un objeto JSON. No enviarlo o enviarlo como
null es un pedido inválido. **{} es una selección vacía, no la configuración
predeterminada del linter.** Las factories ofrecen defaults que activan reglas;
esta API construye siempre el linter a partir de la selección explícita.
Una regla ausente, false o null se interpreta exclusivamente según el reader de
la biblioteca; no se utiliza `defaultConfiguration()` como fallback.
El catálogo incluye el mapa `defaults` de la biblioteca para la versión solicitada. El cliente lo envía explícitamente si quiere aplicarlo.

El catálogo conserva id, type (boolean/integer/choice), acceptsNull, choices y
conflictsWith. Las opciones CHOICE y su validación se obtienen de
`configurationRules()` y `checkConfiguration()`; no se mantienen listas de
opciones ni validadores paralelos en Snippets o en el servicio.

### Resultados

- 200, analyzed=true: compliant indica si hay infracciones de lint.
- 200, analyzed=false: error léxico/sintáctico, compliant=null. Los diagnósticos
  parciales de lint no se presentan como un análisis completo.
- 200 en rules/check con valid=false: configuración inválida, violations por regla.
- 422 en lint con tipo `urn:printscript:problem:invalid-linting-rules`: configuración inválida.
- 422 con supportedVersions: versión no soportada.
- 400 `request-too-large`: código por encima del límite UTF-8.
- 422 `lint-diagnostics-too-large`: el análisis supera el límite de diagnósticos;
  nunca se trunca silenciosamente ni se declara cumplimiento.
- Otros errores, timeout, respuesta incoherente o proveedor caído: falla técnica.

Lint recorre las sentencias del parser y conserva sus errores léxicos/sintácticos.
No ejecuta el programa ni reemplaza la validación semántica usada al guardar.
Un incumplimiento de estilo no invalida por sí solo un guardado.

## Identidad y límites

Identidad del procesador: `capabilities.lint` en GET /processor y header
`Processor-Revision` en todas las respuestas /linting y /linting/**, incluyendo
rechazos. Se calcula con el digest del artefacto y la configuración efectiva de
lint, usando el mismo formato `sha256-v1:<64 hex>`.
Cambiar límites de lint cambia su revisión; no redefine las demás capacidades.
Sin artefacto verificable no se inventa una revisión.

| Propiedad | Default |
| --- | --- |
| linting.code-bytes | 1048576 |
| linting.diagnostic-limit | 1000 |

Los límites positivos acotan entrada y resultados; aumentarlos exige evaluar
memoria y latencia.
Un timeout del cliente no equivale a detener una operación remota.
