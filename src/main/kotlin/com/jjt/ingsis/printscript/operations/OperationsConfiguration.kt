package com.jjt.ingsis.printscript.operations

import com.jjt.ingsis.printscript.execution.ExecutionLimits
import com.jjt.ingsis.printscript.formatting.FormattingService
import com.jjt.ingsis.printscript.linting.LintingService
import com.jjt.ingsis.printscript.processor.ProcessorCapacity
import com.jjt.ingsis.printscript.processor.ProcessorIdentity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
@EnableConfigurationProperties(OperationsSettings::class)
@ConditionalOnProperty("operations.enabled", havingValue = "true")
class OperationsConfiguration {
    @Bean
    fun operationStore(
        redis: StringRedisTemplate,
        settings: OperationsSettings,
    ): OperationStore = RedisOperationStore(redis, settings)

    @Bean
    fun operationEngine(
        linting: LintingService,
        formatting: FormattingService,
        identity: ProcessorIdentity,
    ): OperationEngine = OperationEngine(linting, formatting, identity)

    @Bean(initMethod = "startWorker", destroyMethod = "close")
    fun operationCoordinator(
        store: OperationStore,
        engine: OperationEngine,
        settings: OperationsSettings,
        capacity: ProcessorCapacity,
        limits: ExecutionLimits,
    ): OperationCoordinator =
        OperationCoordinator(store = store, engine = engine, settings = settings, capacity = capacity, limits = limits)
}
