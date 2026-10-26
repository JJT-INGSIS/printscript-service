# SNI-47 — Ejecución incremental interna

Implementación del [contrato v1 de SNI-45](https://github.com/JJT-INGSIS/snippet-searcher-infra/blob/dev/docs/architecture/execution-contract.md).
Este documento explica la implementación; no sustituye la fuente contractual.
`POST /validate` y su catálogo de diagnósticos permanecen sin cambios.

## Responsabilidades y alcance

PrintScript ejecuta; Language adapta HTTP; Snippets autoriza y asocia actores con
sesiones públicas. El navegador **no** debe acceder directamente a estas rutas.
Este servicio no guarda tests, no compara expectativas, no maneja ownership y no
recibe identidades ni UUIDs de snippets. Debe seguir en la red interna.

Se usa `printscript-v1:1.1.0` publicado en Packages, no un proyecto local sustituido.
Cada sesión crea lector, lexer, parser, intérprete y entorno independientes. En 1.0
se ejecutan sus capacidades propias; `readInput` y `readEnv` pertenecen a 1.1.
El proveedor de variables está vacío: ni `PATH` ni secretos del servicio son visibles.

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

## Decisiones aprobadas y costos

Thiago aprobó avanzar con el diseño y carpetas separadas el 26/10/2026, mediante
«dale hacelo apruebo todo», sobre la propuesta de controles cooperativos sin modificar
inicialmente la biblioteca. Canal, NDJSON, límites y retención ya estaban aprobados en SNI-45.

- **Cancelación cooperativa en la integración:** lectores, tokens, statements,
  despacho de instrucciones anidadas e input/output consultan el control de sesión.
  Se usa el punto de extensión público de la factory, sin reemplazar ejecutores
  ni modificar el motor. Se verificó el bytecode publicado y se ejecutaron pruebas
  con esa dependencia. Alternativa: ampliar/publicar la biblioteca; agregaría una
  entrega y coordinación. Si se incorporan nuevas operaciones largas o loops,
  revisar esta decisión y repetir las pruebas antes de declarar compatibilidad.
- **No hay preempción arbitraria de JVM:** una operación indivisible alcanza el
  siguiente checkpoint antes de observar cancelación/timeout. No es un sandbox de
  memoria ni aislamiento frente a fallos fatales de la VM. No se usa Thread.stop,
  un proceso Java por snippet ni se equipara Future.cancel con trabajo detenido.
- **Resultados tipados en la aplicación:** la única excepción de stop es privada a
  la frontera síncrona de la biblioteca, cuyo ProgramInput exige devolver String.
  Se convierte al resultado terminal al salir del intérprete; no cruza HTTP/dominio.
- **Estado transitorio coordinado:** snapshots inmutables con locks/atomics en el
  runtime, no en almacenamiento durable. La coordinación es necesaria para input,
  cancelación y HTTP concurrentes; no hay `var` en producción. Una réplica por
  coordinador, incluyendo actualizaciones: no desplegar start-first con dos contadores.
- **Buffer acotado y terminal independiente:** se puede expulsar historia ya
  entregada, nunca salida pendiente. Si no cabe un evento, el intérprete termina
  por `event-buffer-exceeded`, sin esperar a un consumidor indefinidamente.
- **Flush explícito por evento:** Spring 7 entrega un stream no-flushing al callback;
  el endpoint usa `HttpServletResponse.flushBuffer` y Content-Type explícito. Se
  descartó cambiar una propiedad global del framework; solo afecta esta ruta.

`CANCELLED` se publica después de salir del intérprete. El cupo se libera en el
finally del worker, nunca al cerrar una conexión. La carrera cancelación/finalización
se coordina para fijar un único terminal. Cancelar una sesión terminal conserva su estado.

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
nunca como éxito. No hay Redis, base de ejecuciones ni promesa de reanudación.

Diagnósticos conservan regla, línea y columna desde 1. Los errores runtime agregan
DIVISION_BY_ZERO, INPUT_UNAVAILABLE, INVALID_INPUT_VALUE, ENVIRONMENT_VARIABLE_NOT_FOUND,
INVALID_ENVIRONMENT_VARIABLE_VALUE y UNSUPPORTED_BINARY_OPERATOR, solo en ejecución.
Los mensajes se acotan a 1024 caracteres para que el terminal también esté acotado.

## Verificación reproducible

JDK 21, Docker no requerido por este servicio, y credenciales de Packages en las
variables GITHUB_ACTOR/GITHUB_TOKEN existentes (nunca en archivos versionados):

```bash
./gradlew check build
java -jar build/libs/app.jar --server.address=127.0.0.1 --server.port=18147
```

ExecutionTest prueba la biblioteca publicada; ExecutionLifecycleTest prueba plazos,
limpieza y desconexión; EventJournalTest prueba replay y cotas; ExecutionHttpTest
usa un servidor Spring real y exige output antes de completar el input.
El cliente real de Snippets tiene además su prueba contra este proceso: ver la
documentación SNI-47 de snippets-service. No prueba UI, NGINX, autorización ni despliegue.

### Evidencia local — 26/10/2026

`check build` pasó con 97 pruebas (15 nuevas), sin omitidas. Incluye HTTP Spring
real, runtime publicado, cancelación durante cálculo e input, buffer lleno,
límites de bytes, timeout, replay, reinicio y limpieza. El trabajo queda local
en `feat/sni-47-incremental-execution`, listo para commit/PR; no se desplegó.
