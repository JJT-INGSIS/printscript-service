package com.jjt.ingsis.printscript.validation

import com.jjt.ingsis.printscript.diagnostic.DiagnosticRule
import com.jjt.ingsis.printscript.language.LanguageVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class ValidationServiceTest {
    private val service = ValidationService()

    private val bothVersions = LanguageVersion.entries

    private val onlyV11 = listOf(LanguageVersion.V1_1)

    private val rejectedPrograms =
        listOf(
            Rejection(
                "let a: number = 1 @ 2;",
                bothVersions,
                Finding(DiagnosticRule.UNEXPECTED_CHARACTER, line = 1, column = 19),
            ),
            Rejection(
                "let a: string = \"sin cerrar;",
                bothVersions,
                Finding(DiagnosticRule.UNTERMINATED_STRING, line = 1, column = 17),
            ),
            Rejection(
                "let a: number = 1.2.3;",
                bothVersions,
                Finding(DiagnosticRule.INVALID_NUMBER, line = 1, column = 17),
            ),
            Rejection(
                "let a: number = 1;\nprintln(a)",
                bothVersions,
                Finding(DiagnosticRule.UNEXPECTED_TOKEN, line = 2, column = 11),
            ),
            Rejection(
                "println(b);",
                bothVersions,
                Finding(DiagnosticRule.UNDECLARED_VARIABLE, line = 1, column = 9),
            ),
            Rejection(
                "let a: number;\nprintln(a);",
                bothVersions,
                Finding(DiagnosticRule.UNINITIALIZED_VARIABLE, line = 2, column = 9),
            ),
            Rejection(
                "let a: number = 1;\nlet a: number = 2;",
                bothVersions,
                Finding(DiagnosticRule.ALREADY_DECLARED_VARIABLE, line = 2, column = 1),
            ),
            Rejection(
                "let a: number = \"hola\";",
                bothVersions,
                Finding(DiagnosticRule.TYPE_MISMATCH, line = 1, column = 1),
            ),
            Rejection(
                "let a: string = \"a\" - 1;",
                bothVersions,
                Finding(DiagnosticRule.INVALID_BINARY_OPERANDS, line = 1, column = 21),
            ),
            Rejection(
                "let a: number = -\"a\";",
                bothVersions,
                Finding(DiagnosticRule.INVALID_UNARY_OPERAND, line = 1, column = 17),
            ),
            Rejection(
                "const b: boolean = true;\nb = false;",
                onlyV11,
                Finding(DiagnosticRule.CONSTANT_REASSIGNMENT, line = 2, column = 1),
            ),
            Rejection(
                "let n: number = 1;\nif (n) { println(1); }",
                onlyV11,
                Finding(DiagnosticRule.INVALID_IF_CONDITION, line = 2, column = 5),
            ),
            Rejection(
                "let name: string = readInput(1);",
                onlyV11,
                Finding(DiagnosticRule.INVALID_INPUT_PROMPT, line = 1, column = 30),
            ),
            Rejection(
                "let home: string = readEnv(2);",
                onlyV11,
                Finding(DiagnosticRule.INVALID_ENVIRONMENT_VARIABLE_NAME, line = 1, column = 28),
            ),
        )

    private val programsOnlyValidInV11 =
        listOf(
            OnlyInV11("const limit: number = 10;", line = 1, column = 7),
            OnlyInV11("let done: boolean = true;", line = 1, column = 11),
            OnlyInV11("let name: string = readInput(\"Nombre: \");", line = 1, column = 29),
            OnlyInV11("let home: string = readEnv(\"HOME\");", line = 1, column = 27),
            OnlyInV11(
                "let done: boolean = true;\nif (done) {\n  println(1);\n} else {\n  println(2);\n}",
                line = 1,
                column = 11,
            ),
        )

    @TestFactory
    fun `names the broken rule and where it was broken`(): List<DynamicTest> =
        rejectedPrograms.flatMap { rejection ->
            rejection.versions.map { version ->
                dynamicTest("${rejection.expected.rule} in ${version.label}") {
                    assertThat(findingsOf(rejection.code, version)).containsExactly(rejection.expected)
                }
            }
        }

    @TestFactory
    fun `accepts in 1_1 what 1_0 rejects as a syntax error`(): List<DynamicTest> =
        programsOnlyValidInV11.map { program ->
            dynamicTest(program.code.lineSequence().first()) {
                assertThat(findingsOf(program.code, LanguageVersion.V1_1)).isEmpty()
                assertThat(findingsOf(program.code, LanguageVersion.V1_0))
                    .containsExactly(Finding(DiagnosticRule.UNEXPECTED_TOKEN, program.line, program.column))
            }
        }

    @Test
    fun `accepts in 1_0 a name that 1_1 reserves as a keyword`() {
        val code = "let const: number = 1;\nprintln(const);"

        assertThat(findingsOf(code, LanguageVersion.V1_0)).isEmpty()
        assertThat(findingsOf(code, LanguageVersion.V1_1))
            .containsExactly(Finding(DiagnosticRule.UNEXPECTED_TOKEN, line = 1, column = 5))
    }

    @Test
    fun `accepts empty code in every version`() {
        bothVersions.forEach { version ->
            assertThat(service.validate("", version).valid).isTrue()
        }
    }

    @Test
    fun `reports only the first problem because the library stops there`() {
        assertThat(findingsOf("println(a);\nprintln(b);", LanguageVersion.V1_0))
            .containsExactly(Finding(DiagnosticRule.UNDECLARED_VARIABLE, line = 1, column = 9))
    }

    @Test
    fun `checks the branch that would not run`() {
        val code = "let done: boolean = true;\nif (done) {\n  println(1);\n} else {\n  println(missing);\n}"

        assertThat(findingsOf(code, LanguageVersion.V1_1))
            .containsExactly(Finding(DiagnosticRule.UNDECLARED_VARIABLE, line = 5, column = 11))
    }

    @Test
    fun `does not trust a value assigned in only one branch`() {
        val code = "let done: boolean = true;\nlet a: number;\nif (done) {\n  a = 1;\n}\nprintln(a);"

        assertThat(findingsOf(code, LanguageVersion.V1_1))
            .containsExactly(Finding(DiagnosticRule.UNINITIALIZED_VARIABLE, line = 6, column = 9))
    }

    @Test
    fun `does not run the code, so failures that need values are not validation errors`() {
        assertThat(service.validate("println(1 / 0);", LanguageVersion.V1_0).valid).isTrue()
        assertThat(service.validate("println(readEnv(\"UNDEFINED_VARIABLE\"));", LanguageVersion.V1_1).valid).isTrue()
        assertThat(service.validate("let age: number = readInput(\"Edad: \");", LanguageVersion.V1_1).valid).isTrue()
    }

    @Test
    fun `does not apply linting rules`() {
        val snakeCase = "let my_value: number = 1;\nprintln(my_value + 1);"
        val camelCase = "let myValue: number = 1;\nprintln(myValue + 1);"
        val readInputWithExpression = "let name: string = readInput(\"Nom\" + \"bre: \");"

        bothVersions.forEach { version ->
            assertThat(service.validate(snakeCase, version).valid).isTrue()
            assertThat(service.validate(camelCase, version).valid).isTrue()
        }
        assertThat(service.validate(readInputWithExpression, LanguageVersion.V1_1).valid).isTrue()
    }

    private fun findingsOf(
        code: String,
        version: LanguageVersion,
    ): List<Finding> =
        service.validate(code, version).diagnostics.map { diagnostic ->
            Finding(diagnostic.rule, diagnostic.line, diagnostic.column)
        }

    private data class Finding(
        val rule: DiagnosticRule,
        val line: Int,
        val column: Int,
    )

    private data class Rejection(
        val code: String,
        val versions: List<LanguageVersion>,
        val expected: Finding,
    )

    private data class OnlyInV11(
        val code: String,
        val line: Int,
        val column: Int,
    )
}
