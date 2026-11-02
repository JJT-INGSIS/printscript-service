# SNI-60 — lint real de PrintScript

Conecta el linter publicado en `printscript-v1:1.2.0` (sin modificar el TP)
con HTTP interno y la capacidad Language. No guarda configuraciones/resultados,
no emite ni consume Redis y no modifica creación/actualización ni NDJSON.

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
Un futuro caso de uso que elija defaults debe hacerlo explícitamente.

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

Se extiende exactamente SNI-76: `capabilities.lint` en GET /processor y header
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
memoria/latencia. El cliente conserva los timeouts existentes de PrintScript.
Un timeout del cliente no equivale a detener una operación remota.

## Language

`Language.lintingCatalog`, `checkLintingRules` y `lint` seleccionan la estrategia
`LanguageLinter`. `PrintScriptLinter` utiliza el transporte existente
`PrintScriptExchange`, sin cliente genérico ni nueva jerarquía de transporte.
El mapa de reglas, incluyendo {} y valores null, viaja sin reinterpretación.
Los resultados son tipados: catálogo, chequeo, análisis, código no analizable,
reglas inválidas, límites, capacidad/versión no soportada y falla técnica.
Cada respuesta incluye `Attributed.processor` siguiendo SNI-76; los consumidores
de resultados deben verificarla contra la revisión esperada antes de publicarlos.
El adaptador no convierte fallas técnicas en incumplimientos.

## Decisiones y alcance aprobados

Juan aprobó el plan y sus cuatro condiciones en este chat el 02/11/2026.
Se reutilizan las conventions de SNI-59: reglas explícitas obligatorias, chequeo
separado, selección de capacidad por lenguaje y transporte HTTP existente.
Se descarta usar defaults ante selección vacía porque cambiaría la intención del
usuario; también se descarta duplicar el catálogo/CHOICE desde UI o Snippets.
Se mantiene un contrato específico de lint en vez de generalizar los motores de
formato/lint. Costo: DTOs específicos y traducción de los diagnósticos del TP.

El procesamiento es síncrono en este proveedor. US14/SNI-63 conserva configuración
de usuarios; SNI-64 conecta el reprocesamiento asíncrono. Ninguno se implementa aquí.

## Verificación

Ejecutar `./gradlew build` con JDK 21 en ambos servicios. PrintScript requiere las
credenciales existentes de lectura de GitHub Packages; Snippets usa Docker para
sus integraciones PostgreSQL/Redis, directamente desde Java, sin sockets montados
en runners. No se necesita la VM de dev.

Las pruebas específicas cubren defaults frente a selección vacía, reglas activas/
inactivas, CHOICE y null, diagnósticos Unicode, errores léxicos/sintácticos,
límites, contrato HTTP, identidad de lint, proveedor caído, timeout y dispatch.
