package com.jjt.ingsis.printscript.operations

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

private val insertOperation =
    DefaultRedisScript(
        """
        if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
        if ARGV[2] ~= 'true' and redis.call('SCARD', KEYS[2]) >= tonumber(ARGV[4]) then return -1 end
        redis.call('SET', KEYS[1], ARGV[1])
        if ARGV[2] == 'true' then
          redis.call('PEXPIRE', KEYS[1], ARGV[3])
          redis.call('SREM', KEYS[2], KEYS[1])
        else redis.call('SADD', KEYS[2], KEYS[1]) end
        return 1
        """.trimIndent(),
        Long::class.java,
    )

private val replaceOperation =
    DefaultRedisScript(
        """
        if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
        redis.call('SET', KEYS[1], ARGV[2])
        if ARGV[3] == 'true' then
          redis.call('PEXPIRE', KEYS[1], ARGV[4])
          redis.call('SREM', KEYS[2], KEYS[1])
        else redis.call('SADD', KEYS[2], KEYS[1]) end
        return 1
        """.trimIndent(),
        Long::class.java,
    )

class RedisOperationStore(
    private val redis: StringRedisTemplate,
    private val settings: OperationsSettings,
) : OperationStore {
    private val json = JsonMapper.builder().findAndAddModules().build()
    private val active = "${settings.namespace}:active"

    override fun find(id: UUID): OperationRecord? =
        access {
            redis.opsForValue().get(key(id))?.let { json.readValue(it, OperationRecord::class.java) }
        }

    override fun insert(record: OperationRecord): Boolean =
        access {
            redis.execute(
                insertOperation,
                listOf(key(record.id), active),
                encode(record),
                record.terminal.toString(),
                settings.retention.toMillis().toString(),
                settings.maximumRecords.toString(),
            ) == 1L
        }

    override fun replace(
        before: OperationRecord,
        after: OperationRecord,
    ): Boolean =
        access {
            require(before.id == after.id)
            redis.execute(
                replaceOperation,
                listOf(key(before.id), active),
                encode(before),
                encode(after),
                after.terminal.toString(),
                settings.retention.toMillis().toString(),
            ) == 1L
        }

    override fun pending(): List<UUID> =
        access {
            redis
                .opsForSet()
                .members(active)
                .orEmpty()
                .map { UUID.fromString(it.substringAfterLast(':')) }
                .sorted()
        }

    private fun key(id: UUID): String = "${settings.namespace}:$id"

    private fun encode(record: OperationRecord): String =
        json.writeValueAsString(record).also {
            if (it.toByteArray(Charsets.UTF_8).size > settings.maximumBytes) {
                throw OperationStoreUnavailable(IllegalArgumentException("Operation record exceeds bound"))
            }
        }

    private fun <T> access(operation: () -> T): T =
        try {
            operation()
        } catch (failure: DataAccessException) {
            throw OperationStoreUnavailable(failure)
        } catch (failure: JacksonException) {
            throw OperationStoreUnavailable(failure)
        }
}
