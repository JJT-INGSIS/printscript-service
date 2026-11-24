# Guía de ingeniería de Snippet Searcher

Estas instrucciones se aplican a todos los cambios de código de este repositorio.

El objetivo incluye mantener el código:

- fácil de entender;
- fácil de probar;
- fácil de modificar;
- explícito en sus responsabilidades;
- resistente a la complejidad accidental.

Priorizá soluciones simples, legibles y fáciles de mantener.

Antes de implementar un cambio:

1. Entendé el caso de uso solicitado.
2. Identificá el servicio responsable.
3. Identificá la capa que debe encargarse de cada parte del comportamiento.
4. Mantené la lógica de negocio independiente de HTTP, la persistencia y el framework.
5. Preferí el diseño más pequeño que resuelva correctamente el requisito actual.
6. Implementá historias de usuario futuras solo si se piden explícitamente.
7. Preservá los límites de arquitectura existentes salvo que el pedido exija modificarlos.
8. Buscá oportunidades concretas de simplificar variantes mediante patrones adecuados.
9. Introducí abstracciones o patrones solo si hay un motivo concreto.

# 1. Responsabilidades del sistema

Snippet Searcher se compone de servicios independientes.

Respetá las responsabilidades de cada servicio. Moverlas requiere una razón que vaya más allá de la comodidad de un cambio local.

## snippets-service

Se encarga de:

- los casos de uso de snippets;
- los metadatos de los snippets;
- la coordinación de sus flujos;
- la abstracción `Language`;
- la coordinación con almacenamiento;
- la coordinación con Permissions;
- la coordinación con servicios específicos de cada lenguaje.

Quedan fuera de su responsabilidad:

- la implementación del parser o del validador de PrintScript;
- la persistencia de permisos;
- la autenticación;
- la implementación del almacenamiento en Azure.

Ejemplo:

`snippets-service` puede pedir:

> Validá este código con el lenguaje X y la versión Y.

El funcionamiento interno del parser y de la validación semántica pertenece a PrintScript.

## permissions-service

Se encarga de:

- la titularidad de snippets;
- las relaciones de acceso;
- el acceso compartido;
- los permisos.

Quedan fuera de su responsabilidad:

- la autenticación;
- el contenido de los snippets;
- sus metadatos;
- el comportamiento de PrintScript.

Auth0 se encarga de la autenticación.

## printscript-service

Se encarga de operaciones propias de PrintScript:

- validación;
- ejecución;
- formateo;
- análisis de estilo;
- otras capacidades de PrintScript que requieran las historias de usuario.

Quedan fuera de su responsabilidad:

- los snippets;
- sus metadatos;
- su titularidad;
- los permisos;
- los usuarios.

## Almacenamiento

Usá el contrato de almacenamiento del proyecto al integrar el proveedor respaldado por Azure.

Evitá:

- inventar una interfaz de reemplazo;
- integrar Azure directamente;
- eludir la abstracción provista;
- duplicar el contenido de los snippets como otra fuente de verdad.

Integrá el contrato provisto cuando esté disponible para el caso de uso.

# 2. Límites de arquitectura

Las responsabilidades deben ser explícitas.

Los nombres de paquetes pueden seguir la estructura del repositorio. La relación conceptual entre capas es:

```text
Web / Controlador
        ↓
Aplicación / Caso de uso
        ↓
Dominio
        ↓
Puertos / capacidades
        ↓
Adaptadores de infraestructura
```

Las dependencias deben apuntar hacia la lógica de negocio.

La lógica de negocio se mantiene independiente del framework y de la infraestructura.

Cada clase pertenece a la capa que tiene la responsabilidad de cambiarla.

# 3. Capa web y controladores

La capa web se encarga de HTTP.

Sus responsabilidades incluyen:

- rutas;
- DTO de pedido;
- DTO de respuesta;
- códigos de estado HTTP;
- encabezados HTTP;
- serialización;
- validación de entradas del transporte;
- traducción de resultados de aplicación o dominio a respuestas HTTP.

Las decisiones sobre HTTP quedan fuera de servicios de aplicación, repositorios y clases de dominio.

La regla principal es:

```text
La aplicación o el dominio decide qué pasó.
La capa web decide cómo representarlo mediante HTTP.
```

Por ejemplo, la aplicación puede devolver:

```text
SnippetNotFound
InvalidCode
NotSnippetOwner
UnsupportedLanguage
LanguageServiceUnavailable
```

La capa web decide si esos resultados se convierten en:

```text
404
422
403
400
503
```

según el contrato de la API.

Preferí centralizar la traducción de errores HTTP con mecanismos de Spring Web como `@RestControllerAdvice` cuando simplifique y homogeneice los controladores.

Ejemplo:

```kotlin
@RestControllerAdvice
class SnippetExceptionHandler {

    @ExceptionHandler(SnippetNotFoundException::class)
    fun handleNotFound(): ResponseEntity<ApiError> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiError("snippet_not_found"))
}
```

También podés mapear directamente los resultados explícitos de la aplicación:

```kotlin
@PostMapping("/snippets")
fun create(
    @RequestBody request: CreateSnippetRequest,
): ResponseEntity<*> =
    when (val result = createSnippet.execute(request.toCommand())) {
        is CreateSnippetResult.Success ->
            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result.snippet.toResponse())

        is CreateSnippetResult.InvalidCode ->
            ResponseEntity
                .unprocessableEntity()
                .body(result.toResponse())

        CreateSnippetResult.UnsupportedLanguage ->
            ResponseEntity.badRequest().build()
    }
```

Los servicios de aplicación deben evitar devolver estos tipos:

```kotlin
ResponseEntity<*>
HttpStatus
ServerResponse
```

El código de aplicación, dominio y persistencia es independiente de si el resultado acaba como HTTP 400, 404, 409, 422, 500 o 503.

# 4. Capa de aplicación y casos de uso

Los servicios de aplicación representan los casos de uso del sistema.

Ejemplos:

```text
CreateSnippetUseCase
UpdateSnippetUseCase
RegisterSnippetOwnerUseCase
ValidatePrintScriptUseCase
```

Se encargan de:

- orquestar el caso de uso;
- aplicar reglas de la aplicación;
- coordinar colaboradores;
- decidir el orden de las operaciones de negocio;
- devolver resultados significativos de aplicación o dominio.

Quedan fuera de esta capa:

- códigos de estado HTTP;
- construcción de respuestas HTTP;
- detalles de serialización JSON;
- consultas JPA;
- detalles de Spring MVC;
- configuración de WebClient;
- acceso a variables de entorno;
- configuración de infraestructura de bajo nivel.

## Orquestación declarativa

El método principal de un caso de uso importante debería leerse como una descripción del flujo.

Ejemplo que hace explícitos los pasos:

```kotlin
fun create(command: CreateSnippetCommand): CreateSnippetResult {
    val validation = validateSnippet(command)

    if (validation is ValidationResult.Invalid) {
        return CreateSnippetResult.InvalidCode(validation.diagnostics)
    }

    val contentReference = storeContent(command)
    val snippet = persistMetadata(command, contentReference)

    registerOwner(snippet.id, command.ownerId)

    return CreateSnippetResult.Success(snippet)
}
```

Ejemplo que mezcla responsabilidades:

```kotlin
fun create(command: CreateSnippetCommand): CreateSnippetResult {
    // 100 líneas que mezclan:
    // llamadas a WebClient
    // manejo de estados HTTP
    // conversiones de persistencia
    // detalles de JSON
    // reintentos
    // validación
    // traducción de excepciones
    // titularidad
    // registros
}
```

Los métodos de alto nivel deben comunicar la intención.

Extraé pasos cuando faciliten entender el flujo.

Nombres que describen pasos concretos:

```kotlin
validateSnippet()
storeContent()
persistMetadata()
registerOwner()
checkOwnership()
buildUpdatedSnippet()
```

Evitá extraer pasos con nombres que oculten su intención:

```kotlin
stepOne()
stepTwo()
process()
handle()
executeInternal()
```

El nombre del método debe explicar para qué existe.

# 5. Capa de dominio

El código de dominio representa el problema y sus reglas.

Preferí:

- objetos inmutables;
- estados explícitos;
- resultados de dominio explícitos;
- funciones puras;
- tipos que expresen conceptos del dominio;
- comportamiento independiente de la infraestructura.

Mantené el dominio independiente de:

- Spring;
- controladores;
- HTTP;
- WebClient;
- JPA;
- PostgreSQL;
- variables de entorno;
- API específicas de infraestructura.

La lógica de dominio importante debería poder ejecutarse en una prueba unitaria común sin iniciar Spring.

# 6. Capa de persistencia

La capa de persistencia se dedica a guardar y leer datos.

Puede contener:

- entidades JPA;
- repositorios de Spring Data;
- adaptadores de persistencia;
- conversiones de persistencia;
- consultas específicas de la base.

Quedan fuera de esta capa las decisiones sobre:

- respuestas HTTP;
- códigos de estado HTTP;
- política de autorización;
- flujos de negocio;
- validación de lenguajes;
- orquestación de la aplicación.

Ejemplo que acopla el repositorio a HTTP:

```kotlin
fun findSnippet(id: UUID): ResponseEntity<SnippetEntity>
```

Otro ejemplo de ese acoplamiento:

```kotlin
repository.findById(id)
    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
```

Preferí:

```kotlin
fun findById(id: SnippetId): Snippet?
```

u otro resultado explícito de persistencia o aplicación.

La persistencia informa qué ocurrió al guardar o leer.

La aplicación decide qué significa eso para el caso de uso.

La capa web decide cómo representar el resultado mediante HTTP.

# 7. Clientes de servicios externos

Las integraciones HTTP remotas pertenecen a adaptadores de infraestructura o clientes explícitos.

Ejemplos:

```text
PrintScriptClient
PermissionsClient
```

Se encargan de:

- construir pedidos HTTP;
- serializar y deserializar;
- manejar las URL de los servicios remotos;
- manejar encabezados HTTP;
- interpretar los detalles HTTP de la respuesta;
- configurar tiempos de espera;
- traducir fallos de transporte a fallos de aplicación.

La coordinación del caso de uso queda en la aplicación.

Ejemplo:

```text
printscript-service devuelve HTTP 503
        ↓
PrintScriptClient
        ↓
LanguageServiceUnavailable
        ↓
CreateSnippetUseCase
        ↓
CreateSnippetResult.ServiceUnavailable
        ↓
Controlador / ControllerAdvice
        ↓
Respuesta HTTP
```

Mantené tipos de infraestructura como estos fuera del código de aplicación y dominio:

```text
WebClient
ClientResponse
ResponseEntity
HttpStatus
```

# 8. DTO y modelos

Los modelos en distintas fronteras tienen distintas responsabilidades.

Preferí:

```text
CreateSnippetRequest
        ↓
CreateSnippetCommand
        ↓
Snippet
        ↓
CreateSnippetResponse
```

Las entidades de persistencia quedan fuera de las respuestas de la API HTTP.

Ejemplo que expone la entidad:

```kotlin
@PostMapping
fun create(
    @RequestBody entity: SnippetEntity,
): SnippetEntity
```

Preferí un DTO:

```kotlin
data class CreateSnippetRequest(
    val name: String,
    val description: String,
    val language: String,
    val version: String,
    val content: String,
)
```

Un DTO describe un contrato externo.

Un modelo de aplicación o dominio describe un concepto interno.

Una entidad de persistencia describe el almacenamiento.

Separá modelos cuando compartir la misma clase acople responsabilidades. Evitá duplicarlos por rutina.

# 9. Aplicar SOLID con criterio

Aplicá SOLID cuando mejore el diseño y haya un problema concreto que resolver.

## Principio de responsabilidad única

Una clase debe tener un motivo claro para cambiar.

Ejemplo con responsabilidades mezcladas:

```text
SnippetService
├── interpreta HTTP
├── valida PrintScript
├── llama a Permissions
├── escribe en PostgreSQL
├── accede al almacenamiento
├── traduce errores
└── construye respuestas HTTP
```

Ejemplo con responsabilidades delimitadas:

```text
CreateSnippetUseCase
├── LanguageValidator
├── SnippetContentStorage
├── SnippetRepository
└── OwnershipClient
```

Cada colaborador representa una responsabilidad real.

## Principio abierto/cerrado

Preferí diseños donde sea posible agregar variantes sin modificar reiteradamente el código central de orquestación.

Por ejemplo, agregar un lenguaje o una estrategia de validación debería requerir una implementación nueva, con cambios acotados, en lugar de editar varios `when` dispersos.

Evaluá cada caso. Si no se esperan variantes, un condicional local pequeño puede ser la opción más clara.

## Inversión de dependencias

La lógica de negocio debe depender de capacidades.

Ejemplo:

```kotlin
interface LanguageValidator {
    fun validate(
        code: String,
        version: String,
    ): ValidationResult
}
```

Una implementación de infraestructura podría ser:

```text
PrintScriptLanguageValidator
        ↓
PrintScriptClient
        ↓ HTTP
printscript-service
```

Así el caso de uso queda independiente de HTTP.

## Segregación de interfaces

Dependé de la capacidad más pequeña que necesite quien la consume.

Una interfaz amplia como esta sobra si el consumidor solo valida:

```kotlin
interface LanguageService {
    fun validate(...)
    fun execute(...)
    fun format(...)
    fun lint(...)
    fun debug(...)
    fun transpile(...)
}
```

Preferí:

```kotlin
interface LanguageValidator
```

Agregá otras capacidades a medida que se necesiten.

## Evitar el exceso de abstracción

Evitá:

- crear una interfaz por clase;
- crear fábricas sin un problema real de construcción;
- introducir patrones solo porque existen;
- crear frameworks genéricos para necesidades hipotéticas;
- abstraer código antes de que exista una variación real;
- implementar arquitectura para historias de usuario que no se pidieron.

Preferí código concreto y simple hasta que una frontera o una variación real justifique la abstracción.

# 10. Patrones de diseño para variaciones reales

Buscá oportunidades de usar patrones conocidos cuando faciliten extender, entender o probar el código, y cuando el problema encaje con el patrón.

En proyectos anteriores del equipo fueron útiles:

- Estrategia (Strategy);
- Despachador (Dispatcher);
- Compuesto (Composite);
- Estrategia y Despachador;
- Estrategia y Compuesto.

Podés usar otros patrones si encajan claramente. Conservá el código simple cuando sea la solución más clara.

El objetivo es:

```text
variación o composición real
        ↓
estructura reconocible
        ↓
componentes pequeños e intercambiables
```

La siguiente situación indica que el patrón agrega complejidad:

```text
problema simple
        ↓
patrón innecesario
        ↓
más clases e indirección
```

## Estrategia

Usá Estrategia cuando varios comportamientos intercambiables implementen una misma capacidad.

Algunos candidatos:

- comportamiento que varía por lenguaje;
- comportamiento que varía por versión;
- reglas de validación;
- reglas de formateo;
- reglas de análisis de estilo;
- políticas;
- algoritmos con varias implementaciones.

Ejemplo:

```kotlin
interface LanguageValidator {

    fun supports(
        language: Language,
        version: LanguageVersion,
    ): Boolean

    fun validate(code: String): ValidationResult
}
```

Posibles implementaciones:

```text
PrintScriptV1Validator
PrintScriptV11Validator
FutureLanguageValidator
```

Este enfoque ayuda a evitar condicionales que crecen y se repiten en la aplicación, como:

```kotlin
when (language) {
    PRINTSCRIPT_V1 -> ...
    PRINTSCRIPT_V11 -> ...
    PYTHON -> ...
    JAVASCRIPT -> ...
}
```

Cada estrategia encapsula su variación.

## Despachador

Usá un Despachador cuando existan varias estrategias o manejadores y un componente deba elegir la implementación adecuada.

Ejemplo:

```kotlin
class LanguageValidatorDispatcher(
    private val validators: List<LanguageValidator>,
) {

    fun validate(
        language: Language,
        version: LanguageVersion,
        code: String,
    ): ValidationResult {
        val validator =
            validators.firstOrNull {
                it.supports(language, version)
            } ?: return ValidationResult.UnsupportedLanguage

        return validator.validate(code)
    }
}
```

El consumidor puede trabajar sin conocer qué estrategia concreta atiende el pedido.

Llamada con selección encapsulada:

```text
CreateSnippetUseCase
        ↓
LanguageValidatorDispatcher
        ↓
LanguageValidator apropiado
```

Ejemplo de selección dispersa:

```text
CreateSnippetUseCase
        ↓
if language == ...
else if version == ...
else if ...
```

El Despachador se encarga principalmente de:

1. identificar la implementación adecuada;
2. delegar la operación.

La lógica de negocio de cada implementación sigue en la estrategia.

## Estrategia y Despachador

Usá esta combinación cuando:

1. varias implementaciones compartan una capacidad;
2. haya que elegir una según la entrada;
3. convenga que quienes la invocan sean independientes de cada implementación concreta.

Conceptualmente:

```text
                    ┌── Estrategia A
Consumidor → Despachador ── Estrategia B
                    └── Estrategia C
```

Variaciones habituales:

```text
Lenguaje
Versión
Tipo de regla
Tipo de operación
```

La combinación permite llamadas declarativas y deja cada variante pequeña y comprobable por separado.

## Compuesto

Usá Compuesto cuando haya que tratar como un solo componente a varios que implementan la misma abstracción.

Ejemplo:

```kotlin
interface ValidationRule {
    fun validate(snippet: Snippet): List<ValidationDiagnostic>
}
```

Reglas individuales:

```text
SyntaxRule
VariableDeclarationRule
TypeCompatibilityRule
```

Compuesto:

```kotlin
class CompositeValidationRule(
    private val rules: List<ValidationRule>,
) : ValidationRule {

    override fun validate(
        snippet: Snippet,
    ): List<ValidationDiagnostic> =
        rules.flatMap { it.validate(snippet) }
}
```

El consumidor invoca:

```text
ValidationRule
```

y puede trabajar sin conocer cuántas reglas hay internamente.

## Estrategia y Compuesto

Usá esta combinación cuando varios comportamientos independientes:

- implementen la misma abstracción;
- puedan agregarse o quitarse por separado;
- deban participar todos en la operación.

Conceptualmente:

```text
                   ┌── Estrategia A
Consumidor → Compuesto ├── Estrategia B
                   └── Estrategia C
```

Ejemplos habituales:

- reglas de formateo;
- reglas de análisis de estilo;
- validadores;
- políticas;
- transformaciones.

Así podés agregar comportamientos sin convertir flujos importantes en secuencias largas de condicionales.

## Cuándo usar Despachador o Compuesto

Cada uno resuelve un problema distinto.

Despachador:

```text
hay varias implementaciones
        ↓
elegir UNA
```

Compuesto:

```text
hay varias implementaciones
        ↓
aplicar VARIAS como si fueran una
```

Ejemplo de selección:

```text
Validadores de lenguajes
        ↓
Despachador
        ↓
elegir el validador del lenguaje y versión solicitados
```

Ejemplo de composición:

```text
Reglas de análisis de estilo
        ↓
Compuesto
        ↓
ejecutar todas las reglas aplicables
```

Pueden combinarse cuando el problema exige selección y composición.

## Polimorfismo cuando crecen los condicionales

Un condicional local simple suele ser más claro que introducir varias clases.

Revisá el diseño cuando los condicionales:

- seleccionen el comportamiento según el tipo;
- aparezcan en varios lugares;
- crezcan al agregar variantes;
- obliguen a modificar clases centrales por cada variante.

Las estructuras repetidas como esta:

```kotlin
when (type) {
    A -> handleA()
    B -> handleB()
    C -> handleC()
    D -> handleD()
}
```

pueden indicar que conviene usar estrategias y un despachador.

Conservá un `when` pequeño de uso puntual mientras no haya variación real.

## Patrones para proteger el código importante

Un patrón puede mantener simple el código de negocio que cambia con frecuencia.

Preferí una llamada como:

```text
CreateSnippetUseCase
        ↓
validateSnippet(...)
```

con la selección encapsulada:

```text
LanguageValidatorDispatcher
        ↓
PrintScriptValidator
```

El caso de uso conserva su claridad aunque se agreguen lenguajes o versiones.

Encapsulá la variación fuera de los flujos importantes cuando eso reduzca la complejidad del caso de uso.

## Criterios antes de introducir un patrón

Introducí un patrón para resolver un problema de diseño concreto. Antes de usarlo, comprobá:

1. ¿Qué variación o composición real representa?
2. ¿Elimina decisiones duplicadas o condicionales que crecen?
3. ¿Hay más de una implementación significativa o es razonable que la haya?
4. ¿Facilita entender a quien lo llama?
5. ¿Permite probar los componentes de manera más independiente?
6. ¿Es más simple que la alternativa?

Si la mayoría de las respuestas son negativas, elegí la implementación más simple.

# 11. Inmutabilidad por defecto

Preferí datos inmutables.

En Kotlin, preferí:

```kotlin
val
```

El siguiente ejemplo usa estado mutable:

```kotlin
var
```

Preferí colecciones inmutables y evitá compartir estado mutable.

Ejemplo que modifica estado:

```kotlin
snippet.name = command.name
snippet.language = command.language
snippet.version = command.version
```

Preferí construir el estado siguiente explícitamente:

```kotlin
val updatedSnippet =
    snippet.copy(
        name = command.name,
        language = command.language,
        version = command.version,
    )
```

El estado mutable es aceptable si es:

- local;
- controlado;
- de uso exclusivo;
- considerablemente más simple que su equivalente inmutable.

Usá persistencia durable para el estado que debe sobrevivir reinicios.

# 12. Funciones puras

Usá funciones puras cuando sea práctico para:

- conversiones;
- validación;
- transformaciones;
- cálculos;
- reglas de dominio.

Ejemplo:

```kotlin
fun ValidationResponse.toDomain(): ValidationResult =
    ValidationResult(
        valid = valid,
        diagnostics = diagnostics.map { it.toDomain() },
    )
```

Una función pura:

- depende solo de sus argumentos;
- devuelve un resultado;
- deja intacto el estado externo;
- opera sin acceso a la base de datos;
- opera sin pedidos HTTP;
- opera sin consultar variables de entorno.

Preferí un núcleo funcional con fronteras explícitas para los efectos.

Conceptualmente:

```text
Lógica de negocio pura
        ↓
puertos explícitos
        ↓
efectos secundarios
├── HTTP
├── base de datos
├── almacenamiento
└── registros
```

# 13. Mantener simple el código importante

El código que más cambia y contiene las decisiones de negocio importantes debería ser el más fácil de leer del repositorio.

Priorizá la legibilidad de:

- casos de uso;
- reglas de dominio;
- políticas;
- flujos;
- lógica de validación.

Las clases importantes deben ser:

- pequeñas;
- explícitas;
- enfocadas;
- fáciles de leer de arriba abajo;
- fáciles de probar;
- fáciles de modificar.

Quien abra una clase de caso de uso debería entender el flujo principal sin estudiar primero Spring, HTTP, PostgreSQL o los detalles de un SDK externo.

Encapsulá la complejidad técnica en componentes explícitos de infraestructura.

Algunos ejemplos:

- configuración de Spring;
- configuración de WebClient;
- serialización HTTP;
- conversiones de JPA;
- configuración de PostgreSQL;
- integración con Azure;
- reintentos;
- configuración de tiempos de espera;
- conversión de variables de entorno;
- detalles de Docker;
- adaptadores de frameworks.

El objetivo es:

```text
Código de negocio que cambia con frecuencia
→ pequeño, explícito y fácil de modificar

Detalles estables de infraestructura
→ aislados tras límites claros
```

# 14. Organizar el código para facilitar cambios

Organizá el código según las responsabilidades y motivos de cambio.

Prestá atención a estas dos categorías:

```text
código que cambia con frecuencia
```

```text
detalles técnicos que cambian por otros motivos o con poca frecuencia
```

Mantené el código de negocio que cambia con frecuencia aislado de la complejidad de infraestructura.

Ejemplos de código que cambia con frecuencia:

- orquestación de casos de uso;
- políticas de negocio;
- validación de dominio;
- reglas de autorización;
- decisiones del flujo.

Mantenelo:

- pequeño;
- legible;
- explícito;
- comprobable por separado;
- con poco acoplamiento a infraestructura.

## Aislar los detalles técnicos estables

Ubicá los detalles técnicos estables o secundarios en las fronteras de infraestructura y agrupá según su responsabilidad.

Ejemplos:

```text
configuración del cliente HTTP
serialización
conversiones de base de datos
configuración del framework
conversión de variables de entorno
configuración de reintentos
configuración de tiempos de espera
integración con el SDK de almacenamiento
```

Un cambio de este tipo:

```text
PostgreSQL
WebClient
serialización JSON
almacenamiento en Azure
configuración del ambiente
```

debería afectar la menor cantidad posible de código de negocio.

Del mismo modo, cambiar una regla de negocio debería dejar intacta la infraestructura mientras se conserve el contrato externo.

## Evitar agrupaciones genéricas

Agrupá código estable por responsabilidad. Evitá contenedores genéricos como:

```text
Utils
Common
Helpers
Misc
SharedManager
GeneralService
```

Cada grupo debe conservar una responsabilidad reconocible.

Ejemplo de organización por responsabilidad:

```text
infrastructure/
├── persistence/
├── http/
├── storage/
└── config/
```

Ejemplo de agrupación genérica:

```text
common/
├── Utils
├── Helpers
├── SharedLogic
└── CommonService
```

Ubicá cada tipo de complejidad en el componente responsable.

# 15. Nombres

Los nombres deben comunicar la intención. Preferí nombres específicos.

Nombres claros de clases:

```text
CreateSnippetUseCase
SnippetRepository
PrintScriptValidationClient
RegisterSnippetOwnerUseCase
ValidationDiagnostic
SnippetContentStorage
```

Evitá nombres vagos como estos:

```text
Manager
Helper
Utils
Processor
CommonService
GeneralHandler
ThingService
```

salvo que el término represente un concepto real del dominio.

## Nombres de funciones

Nombres que describen una acción concreta:

```kotlin
validateSnippet()
registerOwner()
storeContent()
findSnippetById()
mapValidationResult()
checkOwnership()
buildUpdatedSnippet()
```

Nombres vagos:

```kotlin
process()
handle()
manage()
doStuff()
executeAction()
runInternal()
```

Los nombres booleanos deberían leerse naturalmente:

```kotlin
isValid
hasAccess
isOwner
canUpdate
```

# 16. Comentarios y documentación

Preferí código que se explique con sus nombres y su estructura.

Reservá los comentarios para explicar el motivo de una decisión. Omití comentarios sobre acciones que el código ya hace evidentes.

Ejemplo redundante:

```kotlin
// Validar snippet
validateSnippet()
```

Ejemplo útil:

```kotlin
// Registrar al dueño después de que el contenido y los metadatos estén disponibles.
// Así Permissions evita exponer un snippet cuyo contenido todavía es inaccesible.
registerOwner(...)
```

Documentá decisiones que no se puedan reconstruir desde el código.

Por ejemplo:

- por qué importa el orden de una operación;
- por qué existe una interfaz;
- qué justifica una estrategia de reintentos;
- por qué se aceptó una limitación.

Versioná la documentación técnica relevante junto con el código. Evitá duplicar lo que ya se entiende al leerlo.

# 17. Manejo de errores

Representá explícitamente los fallos esperados de la aplicación.

Ejemplos:

```text
SnippetNotFound
NotSnippetOwner
InvalidCode
UnsupportedLanguage
UnsupportedVersion
LanguageServiceUnavailable
StorageUnavailable
```

Preferí un resultado explícito para un caso esperado de negocio cuando lo exprese mejor que una excepción genérica.

Evitá capturar todas las excepciones y convertirlas silenciosamente en un fallo genérico.

Conservá la información útil al traducir errores entre fronteras.

Ejemplo:

```text
Diagnóstico de PrintScript
regla + línea + columna
        ↓
frontera de Language
        ↓
CreateSnippetResult.InvalidCode
        ↓
capa web
        ↓
respuesta de error HTTP
```

Conservá los diagnósticos siempre que aporten información.

## Los errores HTTP pertenecen a la capa web

Dejá la construcción y el lanzamiento de errores específicos de HTTP en la capa web. Las siguientes capas deben quedar independientes:

- clases de dominio;
- servicios de aplicación;
- repositorios;
- adaptadores de persistencia.

Ejemplo incorrecto dentro de un repositorio o caso de uso:

```kotlin
throw ResponseStatusException(
    HttpStatus.NOT_FOUND,
    "Snippet not found",
)
```

Representá el resultado con un concepto de aplicación o dominio:

```text
SnippetNotFound
```

y traducilo en la capa web.

## Traducción de errores de persistencia

Una operación de base de datos puede informar un fallo de persistencia. La traducción a una respuesta como esta se hace en capas superiores:

```text
500 Internal Server Error
404 Not Found
409 Conflict
```

## Traducción de errores HTTP remotos

Un cliente HTTP externo puede recibir:

```text
404
409
500
503
```

El cliente o adaptador traduce el detalle del transporte a un fallo de aplicación con significado.

Ejemplo:

```text
HTTP 503 de PrintScript
        ↓
PrintScriptClient
        ↓
LanguageServiceUnavailable
```

El resto de la aplicación puede manejar ese resultado sin conocer la implementación HTTP del proveedor.

# 18. Configuración

Cargá desde fuera de la aplicación la configuración que varía entre ambientes.

Por ejemplo:

- URL de servicios;
- URL de bases de datos;
- credenciales;
- puertos;
- tiempos de espera;
- indicadores de activación;
- destinos de almacenamiento.

Ejemplo con configuración fija:

```kotlin
val permissionsUrl = "http://localhost:8081"
```

Preferí configuración externa:

```kotlin
@ConfigurationProperties("services.permissions")
data class PermissionsProperties(
    val baseUrl: String,
)
```

El mismo artefacto debe poder funcionar en distintos ambientes cambiando su configuración.

# 19. Secretos

Mantené fuera de Git:

- contraseñas;
- tokens de acceso personales;
- claves de API;
- credenciales privadas.

Excluí secretos de:

- código fuente;
- archivos `.env` versionados;
- YAML versionados con credenciales reales;
- Dockerfiles;
- datos de prueba.

Usá variables de entorno o el mecanismo de secretos de la plataforma.

# 20. Registros

Escribí los registros de aplicación a stdout/stderr. Creá archivos de registro administrados por la aplicación solo si se exige explícitamente.

Incluí contexto operativo útil y excluí:

- contraseñas;
- tokens de acceso;
- secretos;
- credenciales privadas;
- información sensible de autenticación.

# 21. Dependencias

Hacé que las dependencias sean explícitas y reproducibles.

- Declaralas en Gradle.
- Preferí versiones fijas.
- Evitá depender de software instalado manualmente en la máquina de un integrante.
- Reutilizá la biblioteca estándar o una dependencia existente si resuelven el problema.
- Mantené fuera del cambio las actualizaciones de dependencias ajenas al ticket.

Cada dependencia aumenta el costo de mantenimiento y el riesgo de la cadena de suministro.

# 22. Pruebas

Las pruebas protegen el comportamiento y reducen el riesgo de futuros cambios.

## Pruebas unitarias

Preferí pruebas unitarias para:

- reglas de dominio;
- funciones puras;
- conversiones;
- validación;
- comportamiento de la aplicación sin infraestructura real.

Una prueba unitaria normalmente puede ejecutarse sin:

- acceso a la red;
- PostgreSQL;
- Docker;
- iniciar toda la aplicación Spring.

## Pruebas de integración

Usá pruebas de integración para fronteras reales como:

- PostgreSQL;
- clientes HTTP;
- serialización;
- configuración de componentes Spring;
- adaptadores de persistencia;
- integración entre componentes de infraestructura.

## Probar el comportamiento observable

Ejemplo de una afirmación sobre el resultado:

```text
el código PrintScript inválido se rechaza y se conservan sus diagnósticos
```

Ejemplo que depende de la implementación:

```text
se llamó exactamente una vez a validate()
```

Evitá pruebas que fallen solo porque se refactorizó un detalle privado.

## Diseñar para facilitar las pruebas

Si para probar lógica de negocio importante necesitás muchos reemplazos simulados, revisá el diseño.

Entre las causas habituales están:

- dependencias ocultas;
- demasiadas responsabilidades;
- estado mutable compartido;
- infraestructura mezclada con negocio;
- estado global.

La lógica de negocio importante debería poder probarse sin infraestructura.

## Probar las estrategias por separado

Al usar Estrategia, Despachador o Compuesto:

- probá cada estrategia de manera independiente;
- probá la selección del despachador aparte del comportamiento de la estrategia;
- probá la coordinación del compuesto aparte de las reglas individuales cuando sea útil.

Evitá pruebas tan amplias que dificulten identificar qué estrategia o regla falló.

## Cobertura

Usá la cobertura como información para orientar la revisión. La corrección requiere afirmaciones significativas que comprueben el comportamiento relevante.

# 23. Análisis estático y formato

Las herramientas configuradas forman parte de los criterios del proyecto.

Respetá:

- el compilador de Kotlin;
- ktlint;
- Detekt;
- las pruebas;
- la construcción con Gradle.

Evitá:

- deshabilitar una regla global para que pase un cambio;
- comentar una prueba que falla;
- ignorar un fallo de compilación o verificación;
- suprimir advertencias ampliamente sin justificación.

Cuando sea necesario suprimir una advertencia:

- hacelo en el lugar más acotado posible;
- explicá el motivo con claridad.

# 24. Construcción y reproducibilidad

El proyecto debe poder verificarse con un solo comando reproducible:

```bash
./gradlew build
```

La construcción debe compilar, ejecutar los análisis estáticos y las pruebas configuradas y empaquetar la aplicación.

Mantené documentada cualquier preparación manual necesaria para ejecutarla. Da por terminado el trabajo cuando la construcción pase.

# 25. Mantener sana la rama principal

Los cambios destinados a integrarse deben dejar en verde la construcción del proyecto.

Corregí estas situaciones:

- construcciones que fallan;
- pruebas ignoradas;
- comprobaciones deshabilitadas;
- omisiones temporales del linter;
- TODO pendientes creados solo para que pase la tarea.

Si el cambio rompe la construcción, corregilo antes de dar la implementación por terminada. Si la rama principal queda en rojo, corregí o revertí la causa.

# 26. Mantener el alcance enfocado

Implementá el ticket o caso de uso solicitado. Dejá las historias futuras y las refactorizaciones amplias ajenas al pedido para otros cambios.

Si aparece un problema no relacionado:

1. registralo;
2. creá o proponé un ticket separado si hace falta;
3. conservá el alcance del cambio actual.

Cada PR debería tener un propósito coherente.

# 27. Evitar arquitectura especulativa

Implementá funcionalidades que respondan a necesidades actuales.

Antes de introducir una abstracción, preguntate:

1. ¿La necesita el requisito actual?
2. ¿Representa una frontera real de arquitectura?
3. ¿Existe variación concreta?
4. ¿Hace que el código importante sea más fácil de entender?
5. ¿Estrategia, Despachador, Compuesto u otro patrón simplifican realmente esa variación?

Si la respuesta es negativa, elegí la solución más simple.

# 28. Mantener el framework en los bordes

Spring pertenece a la infraestructura. Acotá sus detalles al borde y mantené el negocio centrado en el problema.

Preferí anotaciones y configuración del framework en:

- controladores;
- clases de configuración;
- adaptadores;
- implementaciones de persistencia.

Diseñá las clases de negocio a partir del problema que resuelven.

# 29. Hacer visibles los efectos secundarios

El código debe dejar claro cuándo produce efectos secundarios.

Ejemplos:

```text
repository.save(...)
permissionsClient.registerOwner(...)
contentStorage.store(...)
printScriptClient.validate(...)
```

Evitá esconder efectos importantes dentro de:

- constructores;
- getters;
- accesos a propiedades;
- funciones de conversión;
- transformaciones aparentemente puras.

Ejemplo problemático si `toSnippet()` realiza HTTP o persistencia:

```kotlin
val snippet = request.toSnippet()
```

Las funciones de conversión normalmente deben ser puras.

# 30. Preferir flujos explícitos

El comportamiento importante debe poder verse desde el caso de uso.

Ejemplo:

```kotlin
fun update(command: UpdateSnippetCommand): UpdateSnippetResult {
    val current = findSnippet(command.snippetId)

    checkOwnership(
        actorId = command.actorId,
        snippetId = current.id,
    )

    val candidate = buildUpdatedSnippet(current, command)

    validateSnippet(candidate)
    updateContent(candidate)
    updateMetadata(candidate)

    return UpdateSnippetResult.Success(candidate)
}
```

Evitá ocultar comportamiento de negocio importante en:

- hooks del ciclo de vida de entidades;
- listeners con comportamiento implícito;
- callbacks inesperados del framework;
- setters con efectos secundarios;
- interceptores demasiado genéricos.

Usá mecanismos del framework cuando simplifiquen infraestructura y mantené explícitos los flujos de negocio.

# 31. Preferir composición cuando una clase acumula responsabilidades

Si una clase tiene muchos colaboradores sin relación entre sí, revisá su responsabilidad.

Un caso de uso puede coordinar varios colaboradores. Los detalles internos de cada uno quedan en su implementación.

Señales para revisar:

- una clase tiene muchas dependencias que no están relacionadas;
- los métodos operan sobre conceptos distintos;
- motivos distintos obligan a modificar la misma clase;
- la clase mezcla HTTP, persistencia, conversiones y reglas de negocio;
- entender un método exige conocer gran parte del framework.

Preferí componer componentes pequeños con responsabilidades claras.

# 32. Compartir la lógica de negocio entre puntos de entrada

Los puntos de entrada del mismo caso de uso deben llegar a la misma lógica de aplicación.

Por ejemplo:

```text
Crear un snippet desde un archivo
Crear un snippet desde el editor del navegador
        ↓
CreateSnippetUseCase
```

Compartí la implementación de negocio aunque la entrada llegue desde distintos mecanismos de la interfaz.

También aplica a:

```text
Actualizar desde un archivo
Actualizar desde el editor
        ↓
UpdateSnippetUseCase
```

Las diferencias de transporte se resuelven en el borde. El comportamiento de negocio vive en el caso de uso compartido.

# 33. Ubicar las conversiones en su frontera

La lógica de conversión debe vivir cerca del límite que traduce.

Ejemplos:

```text
DTO HTTP → comando de aplicación
    frontera web

Entidad JPA → modelo de dominio
    frontera de persistencia

Respuesta HTTP remota → modelo de aplicación
    frontera del cliente o adaptador HTTP
```

Evitá concentrar conversiones ajenas entre sí en una clase global como:

```text
MapperUtils
```

# 34. Dificultar la representación de estados inválidos

Cuando sea práctico, representá explícitamente los conceptos importantes con tipos que distingan sus responsabilidades.

Ejemplo cuando está justificado:

```kotlin
@JvmInline
value class SnippetId(
    val value: UUID,
)
```

Esto permite distinguir identificadores que de otra forma circularían todos como `UUID`.

Otro ejemplo:

```kotlin
sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Invalid(
        val diagnostics: List<ValidationDiagnostic>,
    ) : ValidationResult
}
```

La siguiente representación deja implícitas las diferencias entre resultados:

```kotlin
Pair<Boolean, List<String>>
```

Introducí tipos envoltorio solo cuando:

- prevengan errores realistas;
- aclaren conceptos importantes del dominio;
- dificulten construir combinaciones inválidas.

# 35. Antes de cambiar código existente

Antes de refactorizar o reemplazar una parte de la arquitectura:

1. entendé por qué existe el código actual;
2. revisá las pruebas existentes;
3. revisá las interfaces y contratos públicos;
4. preservá el comportamiento salvo que el ticket lo cambie explícitamente;
5. evitá reescrituras amplias cuando alcanza un cambio puntual.

Conservá el código funcional ante preferencias de estilo. Si la implementación actual resuelve bien el problema, extendela.

# 36. Verificar los cambios generados con IA

Un agente de IA colabora en la implementación y la revisión. La corrección se comprueba con las herramientas automatizadas del proyecto.

El flujo esperado es:

```text
La IA propone o implementa
        ↓
compilador
        ↓
análisis estático
        ↓
pruebas
        ↓
construcción
```

El agente debe usar su criterio para proponer un diseño. Las comprobaciones automatizadas del proyecto proveen una verificación reproducible.

# 37. Antes de terminar un cambio

Verificá todos estos puntos:

- ¿La responsabilidad está en el servicio correcto?
- ¿La responsabilidad está en la capa correcta?
- ¿Los detalles HTTP se limitan al borde web?
- ¿El código de aplicación, dominio y persistencia queda libre de códigos de estado HTTP?
- ¿La persistencia está aislada de HTTP y de la orquestación de negocio?
- ¿Los detalles del HTTP externo quedan en clientes y adaptadores?
- ¿El código de negocio importante es pequeño y fácil de leer?
- ¿Las clases que más cambian siguen simples y enfocadas?
- ¿El flujo principal se entiende al leer el método principal del caso de uso?
- ¿Los métodos importantes son declarativos?
- ¿Los nombres describen la intención?
- ¿Se evita el estado mutable cuando es práctico?
- ¿Las transformaciones o reglas de dominio podrían ser puras?
- ¿Los efectos secundarios son explícitos?
- ¿Los detalles técnicos estables están aislados de la lógica que cambia seguido?
- ¿Los detalles estables se agrupan por responsabilidad?
- ¿Se evitaron agrupaciones genéricas como `Utils`, `Helpers` o `Common`?
- ¿Había una oportunidad concreta para Estrategia, Despachador, Compuesto u otro patrón?
- Si se introdujo un patrón, ¿simplifica una variación real con una indirección justificada?
- Si hay comportamientos intercambiables, ¿conviene representarlos como estrategias?
- Si hay que elegir una implementación, ¿un despachador simplificaría a quien la invoca?
- Si varias implementaciones actúan como una, ¿un compuesto simplificaría a quien las usa?
- ¿Se evitaron abstracciones innecesarias?
- ¿Se evitaron dependencias innecesarias?
- ¿Los DTO y las entidades de persistencia están separados donde corresponde?
- ¿Los errores esperados se representan explícitamente?
- ¿Los errores HTTP se traducen en el borde web?
- ¿Las pruebas cubren el comportamiento relevante?
- ¿Las estrategias y reglas se pueden probar independientemente cuando aplica?
- ¿Las fronteras de infraestructura tienen pruebas de integración donde corresponde?
- ¿La configuración es externa?
- ¿Los secretos quedan fuera del repositorio?
- ¿Los registros son apropiados para stdout/stderr?
- ¿El cambio conserva el alcance del ticket?
- ¿Se dejaron fuera las historias de usuario futuras que no se pidieron?
- ¿Se preservó el comportamiento fuera del alcance?
- ¿Pasa `./gradlew build`?

Si alguna respuesta es negativa, corregí el problema o justificá explícitamente la excepción.
