# Identidad del procesador

`GET /processor` publica `artifact` con el SHA-256 del JAR en ejecución
y `capabilities` con las revisiones `validate`, `execute`, `format` y `lint`.
Cada revisión tiene formato `sha256-v1:<64 hex>`. Combina el nombre de la
capacidad, el hash del JAR y su configuración efectiva. Validación usa
una configuración vacía. Ejecutar, formatear y lintear incluyen sus límites.

Las rutas `/validate`, `/executions/**`, `/formatting/**` y `/linting/**`
devuelven `Processor-Revision` para su capacidad, también en errores.
La revisión de la respuesta acredita qué procesador atendió esa operación.
El manifiesto informa la identidad disponible al consultarlo. Sin un JAR
identificable, el manifiesto devuelve valores nulos y se omite el header.

Para verificar una imagen, compará `artifact` con `sha256sum /app/app.jar`
dentro del contenedor. El valor de `Processor-Revision` debe coincidir con la
capacidad correspondiente del mismo artefacto y configuración. La versión
del lenguaje se elige por pedido. Activar una revisión nueva del parser
para revalidar snippets es una operación administrativa de Snippets.
