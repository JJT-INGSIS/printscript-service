# Ejecución y operaciones durables

PrintScript ejecuta código. Snippets autoriza, asocia usuarios con sesiones
y guarda las definiciones y resultados de tests. La API interna se consume
por HTTP desde Snippets. Cada sesión crea su propio intérprete y usa un
proveedor de variables de entorno vacío. `readInput` y `readEnv` pertenecen a 1.1.


## API

Todos los UUID son canónicos, minúsculos y con guiones. Las entradas se decodifican
estrictamente: se rechazan tipos incorrectos, campos desconocidos, null y campos
de actor/owner. El código vacío es un string válido de entrada.

| Ruta | Resultado |
| --- | --- |
| POST `/executions` | 201 JSON, Location y descriptor interno |
| GET `/executions/{id}` | 200 descriptor, o 404 si falta/expiró |
| GET `/executions/{id}/events?after=0` | 200 `application/x-ndjson`, un consumidor por sesión |
| POST `/executions/{id}/inputs` | 200 `{requestId, accepted:true}` |
| POST `/executions/{id}/cancel` con `{}` | 202 `{state}`; no significa worker liberado todavía |

Inicio TEST: `{"code":"println(1);","version":"1.0","mode":"TEST","inputs":[]}`.
Inicio INTERACTIVE omite `inputs`; en TEST es obligatorio. La versión nunca tiene
default. El descriptor contiene executionId, mode, state, limits efectivos y lastSeq.

Inputs: `{requestId,value}`, preservando vacíos, duplicados y Unicode. Solo se
acepta el requestId pendiente. Repetirlo con el mismo valor no vuelve a consumirlo;
con otro valor es conflicto. TEST consume su lista en orden y no espera al navegador.

Los errores usan Problem Details y un `type` estable `urn:printscript:problem:<reason>`:
400 pedido/cursor inválido; 404 sesión ausente; 409 input, consumidor o historial en
conflicto; 422 versión no soportada con supportedVersions; 429 capacidad agotada.
Una falla técnica no se presenta como código inválido ni ejecución completada.
El inicio no es idempotente: no repetir automáticamente un POST cuyo resultado se perdió.

La cancelación es cooperativa. El intérprete comprueba los límites y la
cancelación en los puntos de control. El cupo se libera al terminar el
trabajo. El stream usa un único terminal y entrega eventos incrementalmente.


## Límites y operación

Configurar con propiedades Spring `execution.*` o variables equivalentes
`EXECUTION_TOTAL_SECONDS`, `EXECUTION_BUFFER_BYTES`, etc. Se devuelven valores efectivos.

Defaults: 4 activos globales sin cola; 120 s totales; 30 s por input; 15 s sin
consumidor antes de cancelar; 1 MiB de código; 8 KiB por input; 1 MiB de salida;
256 eventos/256 KiB de buffer; terminal retenido 60 s. Los tamaños se cuentan en
UTF-8; el buffer cuenta JSON serializado más LF. Buffer mínimo 512 bytes para control.
Heartbeat cada 10 s. `spring.mvc.async.request-timeout=130s` acota la respuesta MVC;
si se aumenta el timeout del intérprete, revisar también MVC y el consumidor.

Replay conserva secuencia contigua. Cursor futuro/negativo es 400; anterior a la
historia retenida es 409; segundo consumidor activo es 409. Reconectar no inicia
otro programa. Una sesión que nunca abre stream tiene la misma ventana de 15 s.
Cerrar ordenadamente el servicio solicita INTERRUPTED y espera salida de workers.
Una muerte abrupta pierde las sesiones: al reiniciar los IDs anteriores dan 404;
Snippets debe tratar una sesión activa perdida como interrupción/incertidumbre,
nunca como éxito. Las sesiones interactivas viven en memoria.

Diagnósticos conservan regla, línea y columna desde 1. Los errores runtime agregan
DIVISION_BY_ZERO, INPUT_UNAVAILABLE, INVALID_INPUT_VALUE, ENVIRONMENT_VARIABLE_NOT_FOUND,
INVALID_ENVIRONMENT_VARIABLE_VALUE y UNSUPPORTED_BINARY_OPERATOR, solo en ejecución.
Los mensajes se acotan a 1024 caracteres para que el terminal también esté acotado.

## Operaciones durables

Los lotes de lint y formato usan una API independiente de las sesiones NDJSON.

- `PUT /operations/{id}` inicia o recupera una operación. Snippets conserva el UUID y manda `kind` (LINT o FORMAT), `version`, `code`, `rules`, `processorRevision` y `submittedAt`. Reenviar el mismo comando recupera el registro. Un comando distinto con el mismo ID devuelve 409. Para operaciones nuevas, PrintScript verifica la revisión y la ventana de admisión, de ±2 minutos por defecto.
- `GET /operations/{id}` consulta el estado o resultado. Un 404 admite que un PUT demorado todavía pueda llegar.
- `POST /operations/{id}/cancel` registra una cancelación incluso si aún falta el PUT. Una operación en marcha conserva su reserva hasta que el cómputo termina. El header `Execution-Active-Limit` informa la capacidad y puede usarse como precondición en PUT.
- Redis conserva los registros activos sin TTL y los terminales durante siete días por defecto. Un reinicio puede retomar trabajo puro si la revisión coincide. Un fallo al persistir el resultado se reintenta sin repetir el cómputo local.
- La capacidad se comparte con la ejecución interactiva. Hay hasta un cómputo de fondo y se reserva capacidad para ejecución manual. Se admite un proveedor por namespace de Redis con reemplazo `stop-first`.

Está deshabilitado por defecto. Configurar Redis privado, `OPERATIONS_NAMESPACE`
por ambiente, `PRINTSCRIPT_OPERATIONS_ENABLED=true` y
`PRINTSCRIPT_OPERATIONS_DEPLOYMENT_MODE=single-stop-first`. Antes de activar
los workers de Snippets, verificar el proveedor y conciliar las operaciones
síncronas anteriores que todavía estén en estado incierto.
