package com.jjt.ingsis.printscript.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class EventJournalTest {
    @Test
    fun `only delivered history may be evicted and replay never restarts silently`() {
        val journal = EventJournal(UUID.randomUUID(), ExecutionLimits(bufferEvents = 2)) { 100 }
        assertThat(journal.append("started", emptyMap())).isTrue()
        assertThat(journal.append("output", mapOf("text" to "first"))).isTrue()
        assertThat(journal.connect(0)).isInstanceOf(ExecutionResult.Success::class.java)
        assertThat(
            journal.connect(0),
        ).isEqualTo(ExecutionResult.Rejected(Rejection.CONFLICT, "execution-consumer-conflict"))
        assertThat(journal.append("output", mapOf("text" to "pending"))).isFalse()
        journal.delivered(1)
        assertThat(journal.append("output", mapOf("text" to "pending"))).isTrue()
        journal.disconnect()
        assertThat(
            journal.connect(0),
        ).isEqualTo(ExecutionResult.Rejected(Rejection.CONFLICT, "event-history-unavailable"))
        assertThat(journal.connect(4)).isEqualTo(ExecutionResult.Rejected(Rejection.INVALID, "invalid-cursor"))
        assertThat(journal.connect(1)).isInstanceOf(ExecutionResult.Success::class.java)
        assertThat(journal.next(1)?.seq).isEqualTo(2)
    }

    @Test
    fun `bytes as well as event count bound history and terminal has separate control slot`() {
        val journal = EventJournal(UUID.randomUUID(), ExecutionLimits(bufferBytes = 512, bufferEvents = 10)) { 256 }
        repeat(2) { assertThat(journal.append("output", emptyMap())).isTrue() }
        assertThat(journal.append("output", emptyMap())).isFalse()
        journal.finish(mapOf("outcome" to ExecutionState.LIMIT_EXCEEDED))
        journal.finish(mapOf("outcome" to ExecutionState.COMPLETED))
        assertThat(journal.next(2)?.data?.get("outcome")).isEqualTo(ExecutionState.LIMIT_EXCEEDED)
        assertThat(journal.next(2)?.seq).isEqualTo(3)
    }
}
