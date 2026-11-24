# Guía de ingeniería de Snippet Searcher

Estas instrucciones se aplican a todos los cambios de código del repositorio.
La [guía de ingeniería ampliada](docs/engineering-guidelines.md) desarrolla los
criterios de diseño, sus ejemplos y las comprobaciones.

## Alcance

- Identificá el caso de uso, el servicio responsable y la capa correspondiente.
- Revisá código, pruebas, interfaces y contratos públicos antes de editar.
- Conservá el comportamiento y los límites de arquitectura salvo que el pedido
  los cambie. Implementá solo el alcance solicitado y mantené cada PR enfocada.
- Registrá los problemas ajenos al cambio para tratarlos por separado.

## Responsabilidades

- **Snippets** coordina sus casos de uso, sus metadatos, el módulo `Language`,
  el contenido y las llamadas a Permissions y a los servicios de lenguaje.
- **Permissions** guarda ownership y relaciones de acceso. Auth0 gestiona la
  autenticación y las credenciales de usuario.
- **PrintScript** valida, ejecuta, formatea y analiza código PrintScript. Snippets
  conserva los datos de los snippets, aplica autorización y coordina los trabajos.
- **Almacenamiento**. Usá el contrato de almacenamiento existente. Conservá la
  abstracción al integrar proveedores y evitá fuentes de contenido paralelas.

## Capas y contratos

- La capa web resuelve rutas, DTO, serialización, validación del transporte
  y traducción de resultados a HTTP. `ResponseEntity`, `HttpStatus` y
  `ResponseStatusException` pertenecen a esa capa.
- La aplicación coordina casos de uso mediante pasos explícitos. El dominio
  conserva reglas y resultados sin depender de Spring, HTTP o persistencia.
- Los puertos exponen las capacidades que necesita la aplicación. Los
  adaptadores encapsulan repositorios, almacenamiento y clientes HTTP.
- La persistencia informa el resultado de guardar o leer. La aplicación
  decide permisos, reglas y flujo de negocio. Traducí los errores técnicos
  en su frontera y preservá diagnósticos como regla, línea y columna.
- Usá DTO y entidades separados cuando tengan contratos distintos. Concentrá
  las conversiones en el borde que traduce. Representá los fallos esperados
  con resultados explícitos. Evitá capturas genéricas que oculten la causa.
- Distintos puntos de entrada para una misma operación comparten su caso de uso.

## Diseño y legibilidad

- Priorizá mecanismos simples y responsabilidades enfocadas. Aplicá SOLID
  cuando resuelva una variación o dependencia real. Considerá Estrategia,
  Despachador y Compuesto cuando mejoren la lectura y las pruebas.
- Preferí `val`, objetos y colecciones inmutables, y funciones puras. Acotá el
  estado mutable a un contexto controlado. La memoria local es transitoria.
- Hacé explícitas las llamadas HTTP, escrituras y demás efectos. Evitá ocultarlos
  en constructores, getters, callbacks o conversiones.
- Elegí nombres que expresen intención. Agrupá archivos por responsabilidad
  y evitá contenedores genéricos como `Utils` o `Common`.
- Documentá decisiones difíciles de deducir, como orden de pasos, reintentos
  y límites. Explicá el motivo en los comentarios.

## Configuración y seguridad

- Usá configuración externa para URLs, puertos, límites, tiempos de espera,
  dependencias por ambiente y activación de funcionalidades.
- Guardá credenciales y claves fuera de Git, mediante variables de entorno
  o mecanismos de secretos de la plataforma. Excluilas también de fixtures,
  imágenes y registros. Enviá los logs operativos a stdout/stderr.
- Declará dependencias en Gradle con versiones reproducibles. Reutilizá la
  biblioteca estándar y las dependencias actuales. Evitá cambios de versión
  fuera del alcance y preparación local sin documentar.

## Pruebas y entrega

- Probá comportamiento observable. Usá pruebas unitarias para reglas y
  conversiones, e integración para HTTP, serialización, Spring y persistencia.
- Probá estrategias por separado y cubrí la selección o composición donde
  aporte valor. Revisá acoplamiento y efectos si una prueba necesita muchos mocks.
- Respetá el compilador, ktlint, Detekt y las pruebas. Conservá checks y pruebas
  activos. Justificá cualquier supresión en el punto más acotado.
- Para cambios de código, ejecutá `./gradlew build`. Verificá compilación,
  análisis estático, pruebas y empaquetado, también para código generado con IA.
  Si un fallo existente impide verificarlo, registrá el problema con precisión.
