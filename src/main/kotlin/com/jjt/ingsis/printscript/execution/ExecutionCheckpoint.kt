package com.jjt.ingsis.printscript.execution

import printscript.interpreter.ExecutionResult
import printscript.interpreter.SemanticError
import printscript.interpreter.StatementExecutionContext
import printscript.interpreter.StatementExecutor
import printscript.runtime.Environment
import printscript.statement.Statement

// First in the published factory's extension list: checks nested statements too, but never replaces an executor.
internal class ExecutionCheckpoint(
    private val control: ExecutionControl,
) : StatementExecutor<Environment> {
    override fun supportsStatement(statement: Statement): Boolean {
        control.checkpoint()
        return false
    }

    override fun executeStatement(
        statement: Statement,
        context: StatementExecutionContext<Environment>,
    ): ExecutionResult<Environment> = ExecutionResult.Failure(SemanticError.UnsupportedStatement(statement.span))
}
