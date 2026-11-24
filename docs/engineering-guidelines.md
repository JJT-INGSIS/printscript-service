# Guía de ingeniería

La [guía del repositorio](../AGENTS.md) reúne las instrucciones aplicables.
Usá esta referencia para revisar diseño, mantenimiento y pruebas.

## Responsabilidades y flujo

Identificá quién posee cada dato y quién decide cada regla. Snippets orquesta
operaciones de usuario, consulta relaciones en Permissions y delega en
PrintScript por medio de `Language`. PrintScript concentra las capacidades
del lenguaje. Sus respuestas expresan resultados del procesador. Snippets las
usa para decidir si confirma una operación o cómo presenta un resultado.

La capa web traduce el protocolo HTTP al caso de uso y convierte su resultado
en un código de estado y un cuerpo. El caso de uso coordina los pasos de
negocio. El dominio expresa las reglas y resultados. Los adaptadores
implementan las capacidades de persistencia, almacenamiento y HTTP externo.

Ejemplos de límites:

- El validador devuelve diagnósticos con regla y posición. El controlador decide
  cómo representarlos en HTTP.
- Un repositorio devuelve el resultado de persistencia. El caso de uso decide
  qué hacer con una escritura exitosa, un conflicto o un fallo técnico.
- Un cliente externo interpreta el protocolo del proveedor y devuelve un fallo
  de dependencia tipado al caso de uso.
- Un DTO representa el contrato de transporte. Una entidad representa la forma
  de persistir datos. Mantené una conversión explícita cuando ambas difieran.

## Casos de uso y tipos

Hacé que el método principal se pueda leer de arriba abajo como el recorrido de
la operación. Extraé pasos con un nombre que explique su propósito, como
`validateSnippet` o `registerOwner`, cuando eso facilite seguir la secuencia.
Agrupá los detalles estables en adaptadores específicos. Si varias rutas hacen la
misma operación, llevá sus decisiones al caso de uso compartido.

Usá tipos que reduzcan errores reales. Por ejemplo, un resultado explícito de
validación permite distinguir código inválido de una falla del proveedor.
Conservá la regla, la línea y la columna al traducir diagnósticos. Evitá envolver
valores simples si el tipo nuevo carece de una responsabilidad clara.

Preferí funciones puras para reglas, cálculos y conversiones. Mantené visibles
los efectos sobre red, disco, base y logs. Limitá el estado mutable a su dueño
y a su ciclo de vida. Usá almacenamiento durable para los datos que deban
sobrevivir reinicios.

## SOLID y patrones de diseño

Aplicá responsabilidad única por motivo de cambio, interfaces acotadas e
inversión de dependencias en fronteras reales. Extender una capacidad debería
requerir cambios localizados. Preferí composición si una clase acumula
dependencias y reglas ajenas entre sí.

| Patrón | Dónde aporta valor |
| --- | --- |
| Estrategia | Hay implementaciones intercambiables de una capacidad, por ejemplo validar distintas versiones. |
| Despachador | Hay que elegir una estrategia por lenguaje, versión u operación. |
| Compuesto | Hay que evaluar varias reglas o políticas independientes bajo un mismo contrato. |
| Estrategia y Despachador | El punto de entrada selecciona una variante y delega su comportamiento. |
| Estrategia y Compuesto | Cada variante combina las reglas que le corresponden. |

Extraé una abstracción cuando represente un límite real, reúna decisiones
repetidas y simplifique las pruebas. Un condicional corto y local puede ser
suficiente. Evitá fábricas, interfaces y capas genéricas para variaciones
hipotéticas. Probá cada estrategia y, cuando corresponda, la selección del
despachador y el resultado del compuesto de forma independiente.

## Configuración, dependencias y diagnóstico

El mismo artefacto usa configuración externa para funcionar en ambientes
distintos. Pasá secretos mediante los mecanismos de la plataforma. Los logs
salen por stdout/stderr e incluyen contexto operativo útil, sin credenciales
ni datos de autenticación. Declarar dependencias con versiones reproducibles
permite reconstruir el artefacto y evita diferencias por instalaciones locales.

Usá nombres que indiquen la intención de clases, funciones y valores booleanos.
Agrupá código técnico por HTTP, persistencia, almacenamiento o configuración.
Explicá en comentarios y documentación por qué importa un orden, una política
o una limitación. Conservá la documentación relevante junto al código.

## Verificación

Usá pruebas unitarias para reglas puras y decisiones de aplicación. Reservá las
pruebas de integración para comprobar HTTP, serialización, cableado de Spring,
persistencia y proveedores reales. Afirmá resultados observables. El porcentaje
de cobertura ayuda a orientar la revisión.

Antes de integrar código, confirmá las responsabilidades y el comportamiento que
deben conservarse. Verificá cambios con `./gradlew build` y mantené las
comprobaciones de Kotlin, ktlint, Detekt y Gradle activas. Si hace falta una
supresión, acotala al punto que la justifica. Los fallos ajenos al cambio se
registran y se tratan de forma separada.
