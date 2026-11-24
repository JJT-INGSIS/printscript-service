# Guía de ingeniería de Snippet Searcher

Estas instrucciones se aplican a todos los cambios de código de este repositorio.
Priorizá soluciones simples, legibles, comprobables y fáciles de mantener.

## 1. Alcance y preparación

- Tratá cada servicio como un repositorio independiente y respetá sus responsabilidades.
- Antes de implementar, entendé el caso de uso e identificá el servicio y la capa responsables.
- Revisá el código, las pruebas, las interfaces y los contratos públicos antes de cambiar la arquitectura.
- Preservá el comportamiento y los límites de arquitectura existentes salvo que el pedido los cambie explícitamente.
- Extendé el código que ya funciona. Las preferencias de estilo por sí solas no justifican una reescritura.
- Implementá únicamente el ticket solicitado. Dejá fuera historias de usuario futuras y refactorizaciones ajenas al pedido.
- Registrá los problemas ajenos al pedido y proponé resolverlos por separado.
- Mantené un propósito coherente por PR.

La referencia completa, con los ejemplos originales, está en la
[guía de ingeniería](docs/engineering-guidelines.md).
Consultá las secciones correspondientes al tomar decisiones de arquitectura,
elegir patrones, diseñar contratos de error o pruebas, o aclarar estas reglas.
La referencia desarrolla las reglas dentro del alcance solicitado.

## 2. Responsabilidades de los servicios

### snippets-service

Se encarga de los casos de uso de snippets, sus metadatos, sus flujos, la
abstracción `Language` y la coordinación con almacenamiento, Permissions
y servicios específicos de cada lenguaje.

La implementación del parser y la validación de PrintScript, la persistencia
de permisos, la autenticación y la implementación del almacenamiento en Azure
quedan fuera de su responsabilidad.
Puede solicitar validación para un lenguaje y una versión sin conocer el
funcionamiento interno de PrintScript.

### permissions-service

Se encarga de la titularidad de snippets, las relaciones de acceso, el acceso compartido y los permisos.

La autenticación, el contenido, los metadatos y el comportamiento de PrintScript
quedan fuera de su responsabilidad. Auth0 se encarga de la autenticación.

### printscript-service

Se encarga de la validación, la ejecución, el formateo, el análisis de estilo y
otras capacidades propias de PrintScript que exijan las historias solicitadas.

Los snippets, sus metadatos, la titularidad, los permisos y los usuarios
quedan fuera de su responsabilidad.

### Almacenamiento

Usá el contrato de almacenamiento provisto por la cátedra y su implementación
respaldada por Azure cuando estén disponibles para el caso de uso.
Evitá inventar un reemplazo, integrar Azure directamente, eludir la abstracción
o duplicar el contenido como otra fuente de verdad.

## 3. Límites de arquitectura

Seguí la estructura de paquetes existente y mantené explícitas las responsabilidades.
Las dependencias apuntan hacia el negocio. Infraestructura implementa las capacidades requeridas.

- Web y controladores. Rutas, DTO de pedido y respuesta, estados, encabezados,
  serialización, validación del transporte y traducción de resultados a HTTP.
- Aplicación y casos de uso. Orquestación de operaciones, reglas de aplicación,
  coordinación de colaboradores y resultados significativos de aplicación o dominio.
- Dominio. Conceptos y reglas del problema, independientes de HTTP, Spring,
  WebClient, JPA, PostgreSQL, variables de entorno y API de infraestructura.
- Puertos y capacidades. Contratos acotados que necesita la lógica de negocio.
- Infraestructura. Persistencia, clientes externos, configuración y adaptadores técnicos.

Las decisiones HTTP pertenecen a la capa web. La aplicación o el dominio
decide qué ocurrió y la capa web lo representa mediante HTTP.
Usá `@RestControllerAdvice` o conversión explícita de resultados en el controlador
cuando corresponda. Mantené `ResponseEntity`, `HttpStatus`, `ServerResponse` y
`ResponseStatusException` fuera de aplicación, dominio y persistencia.

Ubicá las anotaciones y configuración de Spring en los bordes, como
controladores, configuración y adaptadores. Diseñá las clases de negocio
alrededor del problema que resuelven.

## 4. Casos de uso y flujos

- Mantené pequeño, enfocado y legible de arriba abajo el código de negocio importante.
- Hacé que el método principal del caso de uso describa el flujo en forma declarativa.
- Extraé pasos con intención concreta, como `validateSnippet`, `storeContent`,
  `persistMetadata`, `registerOwner` y `checkOwnership`, cuando aclaren el flujo.
- Los casos de uso coordinan colaboradores. Los detalles internos de infraestructura
  quedan en cada colaborador.
- Mantené fuera de los casos de uso la construcción de respuestas HTTP, los detalles
  de JSON, las consultas JPA, Spring MVC, la configuración de WebClient, el acceso
  al entorno y la configuración de bajo nivel.
- Hacé visibles las operaciones de negocio. Evitá esconderlas en hooks de entidades,
  listeners, setters, constructores o callbacks inesperados del framework.
- Distintos puntos de entrada para la misma operación deben usar el mismo caso de
  uso. Las diferencias de transporte se resuelven en el borde.
- Revisá la responsabilidad de una clase con colaboradores o motivos de cambio ajenos entre sí.
- Aislá las reglas y flujos que cambian con frecuencia de la complejidad técnica estable.

## 5. Persistencia y clientes externos

La persistencia se encarga de entidades JPA, repositorios, consultas,
adaptadores y conversiones de persistencia. Informa resultados de lectura y
escritura. La autorización, los flujos de negocio, la validación de lenguajes,
la orquestación y las respuestas HTTP quedan en sus capas correspondientes.

Las integraciones HTTP remotas pertenecen a clientes o adaptadores explícitos
como `PrintScriptClient` y `PermissionsClient`. Se encargan de pedidos,
serialización, URL, encabezados, tiempos de espera y manejo de respuestas HTTP.
Traducí fallos de transporte a resultados de aplicación como
`LanguageServiceUnavailable`. Mantené `WebClient`, `ClientResponse` y los códigos
HTTP fuera de aplicación y dominio. El caso de uso conserva la coordinación.

## 6. Modelos, conversiones y errores

- Distinguí DTO externos, comandos y resultados de aplicación, modelos de
  dominio y entidades cuando un modelo compartido acople responsabilidades.
- Evitá exponer entidades de persistencia directamente por HTTP.
- Separá clases de modelo cuando lo justifiquen sus responsabilidades.
- Ubicá las conversiones en la frontera que traducen, sea web, persistencia o cliente remoto.
- Mantené puras las conversiones. Evitá esconder HTTP u operaciones de base de datos.
- Usá estados y resultados explícitos y tipos de dominio significativos cuando
  prevengan errores realistas. Agregá tipos envoltorio solo si aportan valor.
- Representá los fallos esperados explícitamente, por ejemplo `SnippetNotFound`,
  `NotSnippetOwner`, `InvalidCode`, `UnsupportedLanguage`, `UnsupportedVersion` y `StorageUnavailable`.
- Usá resultados explícitos para los casos esperados que expresen mejor que una excepción genérica.
- Evitá capturas amplias que conviertan silenciosamente cualquier excepción en un fallo genérico.
- Conservá diagnósticos útiles, incluidos regla, línea y columna, al traducir fallos.
- La capa web traduce fallos de persistencia o de proveedores remotos de acuerdo con el contrato público.

## 7. SOLID y patrones de diseño

Aplicá SOLID con criterio. Buscá un motivo claro por el que cambia cada clase,
capacidades acotadas, dependencias del negocio sobre capacidades y extensiones
localizadas cuando exista variación real.

Considerá patrones cuando simplifiquen un problema concreto:

- Estrategia. Comportamientos intercambiables para la misma capacidad.
- Despachador. Elige la estrategia adecuada y le delega su comportamiento.
- Compuesto. Aplica múltiples componentes como uno mediante una abstracción común.
- Estrategia y Despachador. Elige comportamiento por lenguaje, versión, regla u operación.
- Estrategia y Compuesto. Combina reglas, validadores, formateadores o políticas independientes.

Mantené a los consumidores independientes de las variantes concretas.
Revisá los condicionales por tipo cuando se repitan o crezcan. Conservá ramas
locales y pequeñas cuando sean más claras que agregar clases.

Antes de agregar un patrón o una abstracción, verificá que represente una
frontera, variación o composición real. Debería eliminar decisiones repetidas,
aclarar el uso, mejorar las pruebas independientes y ser más simple que la
alternativa. Evitá interfaces por clase, fábricas innecesarias, frameworks
genéricos y arquitectura para requisitos hipotéticos. Usá patrones al servicio del diseño.

## 8. Inmutabilidad y efectos secundarios visibles

- Preferí `val` en Kotlin, objetos y colecciones inmutables y funciones puras.
- Usá funciones puras para conversiones, validación, transformaciones, cálculos y reglas de dominio.
- El estado mutable es aceptable si es local, controlado, de uso exclusivo y considerablemente más simple.
- Usá persistencia durable para los datos que deban sobrevivir reinicios.
- Conservá los efectos sobre HTTP, base de datos, almacenamiento y registros detrás de fronteras explícitas.
- Evitá ocultar efectos en getters, propiedades, constructores o funciones aparentemente puras.

## 9. Nombres, organización y documentación

- Nombrá clases y métodos por su intención, como `CreateSnippetUseCase`,
  `SnippetRepository` y `PrintScriptValidationClient`.
- Preferí nombres booleanos significativos, como `isValid`, `hasAccess`, `isOwner` y `canUpdate`.
- Evitá nombres vagos como `Manager`, `Helper`, `Utils` o `Processor` salvo
  que expresen un concepto del dominio.
- Agrupá infraestructura por responsabilidad, como persistencia, HTTP,
  almacenamiento y configuración. Evitá agrupaciones genéricas como
  `Common`, `Misc` o un `MapperUtils` global.
- Escribí comentarios que expliquen el motivo de las decisiones.
- Documentá decisiones que no se puedan deducir del código, como el orden de una
  operación, el motivo de una abstracción, las políticas de reintento y los límites aceptados.
- Versioná la documentación relevante junto al código. Evitá repetir lo que ya se entiende al leerlo.

## 10. Configuración, seguridad y dependencias

- Externalizá URL, configuración de base de datos, credenciales, puertos,
  tiempos de espera, indicadores de activación y destinos de almacenamiento.
- Hacé que el mismo artefacto funcione en distintos ambientes mediante configuración externa.
- Mantené contraseñas, tokens, claves de API y credenciales fuera del código,
  archivos de entorno o YAML versionados, Dockerfiles y datos de prueba.
- Usá variables de entorno o mecanismos de secretos de la plataforma.
- Escribí registros útiles a stdout/stderr. Creá archivos de registro
  administrados por la aplicación solo si se requieren explícitamente.
- Excluí de los registros secretos, tokens, credenciales e información sensible de autenticación.
- Declará dependencias en Gradle y preferí versiones fijas y reproducibles.
- Evitá depender de software instalado manualmente sin documentación.
- Preferí la biblioteca estándar o dependencias existentes cuando alcancen.
- Mantené fuera del ticket las actualizaciones de dependencias ajenas al pedido.

## 11. Pruebas y verificación

- Probá comportamiento con afirmaciones significativas. Evitá depender solo
  de detalles privados o del número de llamadas.
- Preferí pruebas unitarias para reglas, funciones puras, conversiones,
  validación y comportamiento de aplicación. Deberían ejecutarse normalmente
  sin iniciar Spring ni usar red, PostgreSQL o Docker.
- Usá integración para persistencia, HTTP, serialización, configuración de Spring y fronteras reales.
- Si una prueba de negocio necesita muchos reemplazos simulados, revisá dependencias
  ocultas, responsabilidades mezcladas, estado compartido y acoplamiento a infraestructura.
- Probá estrategias de forma independiente. Probá por separado la selección del
  despachador y la orquestación del compuesto cuando ayude a identificar los fallos.
- Usá la cobertura como información. Comprobá la corrección con afirmaciones sobre el comportamiento.
- Respetá el compilador de Kotlin, ktlint, Detekt, las pruebas y las comprobaciones de Gradle.
- Mantené activas las reglas globales y las pruebas. Corregí fallos sin introducir
  omisiones temporales o TODO solo para hacer pasar la tarea.
- Si una supresión está justificada, acotala y explicá su motivo.

Para cambios de código, verificá con el comando reproducible del proyecto:

```bash
./gradlew build
```

La construcción debe compilar, ejecutar las comprobaciones y pruebas
configuradas, y empaquetar la aplicación. Documentá cualquier preparación y
dá por terminada la implementación con la construcción en verde.
El código generado con IA requiere las mismas comprobaciones de compilación,
análisis estático, pruebas y empaquetado.
Corregí o reportá explícitamente fallos existentes en la rama principal.

## 12. Antes de terminar

Comprobá que la responsabilidad corresponda al servicio y la capa correctos,
que el flujo sea explícito y que HTTP y persistencia estén aislados. Revisá el
alcance, los nombres, la inmutabilidad, los efectos visibles, la ausencia de
reglas duplicadas y de abstracciones especulativas, la conservación de
diagnósticos, la configuración externa y la ausencia de secretos.
Verificá las pruebas relevantes, la capacidad de probar cada estrategia y la
construcción en verde. Corregí las reglas incumplidas o justificá la excepción.
