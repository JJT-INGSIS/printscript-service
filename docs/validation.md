# Validación de PrintScript

`POST /validate` responde si un código PrintScript es válido para una versión del lenguaje. No ejecuta el código, no lo guarda y no aplica reglas de linting.

Usa la biblioteca publicada `io.github.jjt-ingsis.printscript:printscript-v1:1.1.0`, que valida PrintScript 1.0 y 1.1.

Los ejemplos de este documento son los tests del contrato: `ValidationContractTest` y `ValidationTechnicalFailureTest` leen este archivo, envían cada pedido y comparan la respuesta completa. Si el comportamiento cambia y el ejemplo no, el build falla.

## Pedido

`Content-Type: application/json`

| Campo | Tipo | Obligatorio | Descripción |
| --- | --- | --- | --- |
| `code` | string | Sí | Código fuente completo. Puede ser una cadena vacía. No puede faltar ni ser `null`. |
| `version` | string | Sí | Versión del lenguaje: `"1.0"` o `"1.1"`. No tiene valor por defecto. |

## Respuestas

| Status | Significado | Cuerpo |
| --- | --- | --- |
| `200` | El código se pudo analizar. El veredicto está en `valid`. | Resultado |
| `400` | El pedido no se pudo leer: no es JSON, falta un campo o un campo es `null`. | Error |
| `422` | El pedido se pudo leer, pero pide una versión que el servicio no soporta. | Error con `supportedVersions` |
| `500` | Falla técnica: el servicio no pudo determinar si el código es válido. | Error |

Un código inválido no es un error HTTP: es una respuesta `200` con `valid` en `false`. Un status distinto de `200` nunca dice nada sobre el código.

Los demás errores de protocolo, como un método o un `Content-Type` no admitidos, usan el mismo formato de error con su status estándar.

### Resultado

`Content-Type: application/json`

| Campo | Tipo | Presente | Descripción |
| --- | --- | --- | --- |
| `valid` | boolean | Siempre | `true` cuando no hay diagnósticos. |
| `diagnostics` | lista de Diagnóstico | Siempre | Vacía cuando `valid` es `true`. |

La biblioteca se detiene en el primer problema, así que hoy un resultado inválido trae exactamente un diagnóstico. El campo es una lista para que el contrato no cambie si eso deja de ser así.

### Diagnóstico

| Campo | Tipo | Presente | Descripción |
| --- | --- | --- | --- |
| `rule` | string | Siempre | Identificador estable de la regla incumplida. Ver [Catálogo de reglas](#catálogo-de-reglas). |
| `message` | string | Siempre | Explicación para una persona. Su redacción puede cambiar: para decidir, usar `rule`. |
| `line` | integer | Siempre | Línea donde empieza el problema. La primera línea es la 1. |
| `column` | integer | Siempre | Columna donde empieza el problema. La primera columna es la 1. |

### Error

`Content-Type: application/problem+json`. Es el formato Problem Details (RFC 9457) que Spring usa para sus propios errores.

| Campo | Tipo | Presente | Descripción |
| --- | --- | --- | --- |
| `title` | string | Siempre | Nombre corto del problema. |
| `status` | integer | Siempre | El mismo status de la respuesta. |
| `detail` | string | Siempre | Explicación del problema. |
| `instance` | string | Siempre | Ruta del pedido. |
| `supportedVersions` | lista de string | Solo en `422` | Versiones que el servicio acepta. |

## Ejemplos

En cada ejemplo, el primer bloque es el cuerpo del pedido y el segundo es el cuerpo de la respuesta. El título indica el status.

### Código válido

#### `200` Código válido en 1.0

```json
{
  "code": "let total: number = 2 + 3;\nprintln(total);",
  "version": "1.0"
}
```

```json
{
  "valid": true,
  "diagnostics": []
}
```

#### `200` Código válido en 1.1

```json
{
  "code": "const ready: boolean = true;\nif (ready) {\n  println(\"listo\");\n} else {\n  println(\"esperando\");\n}",
  "version": "1.1"
}
```

```json
{
  "valid": true,
  "diagnostics": []
}
```

### Código inválido

#### `200` Error léxico

```json
{
  "code": "let name: string = 'sin cerrar;",
  "version": "1.0"
}
```

```json
{
  "valid": false,
  "diagnostics": [
    {
      "rule": "UNTERMINATED_STRING",
      "message": "falta cerrar el texto abierto con '",
      "line": 1,
      "column": 20
    }
  ]
}
```

#### `200` Error sintáctico

```json
{
  "code": "let total: number = 5\nprintln(total);",
  "version": "1.0"
}
```

```json
{
  "valid": false,
  "diagnostics": [
    {
      "rule": "UNEXPECTED_TOKEN",
      "message": "se esperaba ';' pero se encontró 'println'",
      "line": 2,
      "column": 1
    }
  ]
}
```

#### `200` Error semántico

```json
{
  "code": "let total: number = 5;\nprintln(totl);",
  "version": "1.1"
}
```

```json
{
  "valid": false,
  "diagnostics": [
    {
      "rule": "UNDECLARED_VARIABLE",
      "message": "la variable 'totl' no fue declarada",
      "line": 2,
      "column": 9
    }
  ]
}
```

### Diferencias entre versiones

PrintScript 1.1 agrega `const`, el tipo `boolean` con `true` y `false`, `if` / `else`, `readInput` y `readEnv`, y reserva esas palabras. Por eso un mismo código puede ser válido en una versión e inválido en la otra, en los dos sentidos.

#### `200` Código de 1.1 validado como 1.0

Es el mismo código del ejemplo "Código válido en 1.1". En 1.0 `const` no es una palabra del lenguaje: se lee como un nombre y lo que sigue ya no encaja.

```json
{
  "code": "const ready: boolean = true;\nif (ready) {\n  println(\"listo\");\n} else {\n  println(\"esperando\");\n}",
  "version": "1.0"
}
```

```json
{
  "valid": false,
  "diagnostics": [
    {
      "rule": "UNEXPECTED_TOKEN",
      "message": "se esperaba '=' pero se encontró 'ready'",
      "line": 1,
      "column": 7
    }
  ]
}
```

#### `200` Nombre libre en 1.0

```json
{
  "code": "let const: number = 1;\nprintln(const);",
  "version": "1.0"
}
```

```json
{
  "valid": true,
  "diagnostics": []
}
```

#### `200` El mismo nombre está reservado en 1.1

```json
{
  "code": "let const: number = 1;\nprintln(const);",
  "version": "1.1"
}
```

```json
{
  "valid": false,
  "diagnostics": [
    {
      "rule": "UNEXPECTED_TOKEN",
      "message": "se esperaba un identificador pero se encontró 'const'",
      "line": 1,
      "column": 5
    }
  ]
}
```

### Pedidos rechazados y fallas

#### `422` Versión no soportada

```json
{
  "code": "println(1);",
  "version": "2.0"
}
```

```json
{
  "title": "Unsupported version",
  "status": 422,
  "detail": "PrintScript version '2.0' is not supported.",
  "instance": "/validate",
  "supportedVersions": ["1.0", "1.1"]
}
```

#### `400` Pedido sin versión

```json
{
  "code": "println(1);"
}
```

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Failed to read request",
  "instance": "/validate"
}
```

#### `500` Falla técnica

El pedido es correcto. El test reemplaza la validación por una que falla, porque una falla técnica no depende de lo que se envía.

```json
{
  "code": "println(1);",
  "version": "1.1"
}
```

```json
{
  "title": "Internal Server Error",
  "status": 500,
  "detail": "The request could not be completed because of a technical failure.",
  "instance": "/validate"
}
```

## Alcance de la validación

**Qué pide la consigna.** Al crear o actualizar un snippet (US1–4), el código tiene que ser válido para el lenguaje y la versión elegidos. Si no lo es, hay que informar qué regla incumple y en qué línea y columna.

**Qué hace este servicio.** Entrega el código al validador de la biblioteca para la versión pedida y traduce su respuesta. No agrega ni quita comprobaciones: "válido" significa "la biblioteca lo acepta".

**Qué comprueba la biblioteca.**

1. Léxico: todos los caracteres pertenecen al lenguaje, los textos se cierran y los números están bien escritos.
2. Sintaxis: cada sentencia tiene la forma que la versión admite.
3. Semántica, sin ejecutar:
    - una variable se declara antes de usarse y recibe un valor antes de leerse;
    - no se declara dos veces el mismo nombre en un mismo bloque;
    - una constante no se reasigna;
    - el valor asignado coincide con el tipo declarado;
    - los operandos son de tipos que el operador admite;
    - la condición de un `if` es `boolean`;
    - el argumento de `readInput` y de `readEnv` es `string`.

   Revisa las dos ramas de cada `if`, incluida la que no se ejecutaría.

**Qué no comprueba.**

- No ejecuta el código. Una división por cero, una variable de entorno que no existe o una entrada que no se puede convertir al tipo esperado son errores de ejecución, no de validación.
- No aplica reglas de linting. La convención de nombres y las restricciones sobre los argumentos de `println` y `readInput` son reglas configurables del linter: un código que las incumple sigue siendo válido.
- Es conservadora: si una variable recibe su valor en una sola rama de un `if`, después del `if` se la considera sin valor.

#### `200` No ejecuta el código

```json
{
  "code": "println(1 / 0);",
  "version": "1.0"
}
```

```json
{
  "valid": true,
  "diagnostics": []
}
```

#### `200` No aplica reglas de linting

```json
{
  "code": "let my_total: number = 1;\nprintln(my_total + 1);",
  "version": "1.0"
}
```

```json
{
  "valid": true,
  "diagnostics": []
}
```

**Límite conocido.** Un código con miles de niveles de anidamiento, por ejemplo miles de paréntesis abiertos, agota la pila de la biblioteca. El servicio responde `500` y sigue funcionando; no lo informa como código inválido porque no llegó a un veredicto.

## Catálogo de reglas

### Léxicas

| `rule` | Significado |
| --- | --- |
| `UNEXPECTED_CHARACTER` | Hay un carácter que no pertenece al lenguaje. |
| `UNTERMINATED_STRING` | Un texto no tiene comilla de cierre. |
| `INVALID_NUMBER` | Un número está mal escrito. |

### Sintácticas

| `rule` | Significado |
| --- | --- |
| `UNEXPECTED_TOKEN` | Se encontró algo distinto de lo que la sentencia admite en ese lugar. |
| `INVALID_LITERAL` | Un literal no se puede interpretar. |

### Semánticas

| `rule` | Significado |
| --- | --- |
| `UNDECLARED_VARIABLE` | Se usa una variable que no fue declarada. |
| `UNINITIALIZED_VARIABLE` | Se lee una variable que todavía no recibió un valor. |
| `ALREADY_DECLARED_VARIABLE` | Se declara un nombre que ya existe en el mismo bloque. |
| `CONSTANT_REASSIGNMENT` | Se asigna un valor a una constante. |
| `TYPE_MISMATCH` | El valor asignado no coincide con el tipo declarado. |
| `INVALID_BINARY_OPERANDS` | Un operador binario recibe tipos que no admite. |
| `INVALID_UNARY_OPERAND` | Un operador unario recibe un tipo que no admite. |
| `INVALID_IF_CONDITION` | La condición de un `if` no es `boolean`. |
| `INVALID_INPUT_PROMPT` | El argumento de `readInput` no es `string`. |
| `INVALID_ENVIRONMENT_VARIABLE_NAME` | El argumento de `readEnv` no es `string`. |
| `UNSUPPORTED_STATEMENT` | La sentencia no está soportada en la versión pedida. |
| `UNSUPPORTED_EXPRESSION` | La expresión no está soportada en la versión pedida. |

### Error no catalogado

| `rule` | Significado |
| --- | --- |
| `UNKNOWN` | La biblioteca informó un error que este servicio no tiene catalogado para la validación. Se conservan la línea y la columna. |