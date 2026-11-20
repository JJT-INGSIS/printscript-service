package com.jjt.ingsis.printscript.execution.http

import com.jjt.ingsis.printscript.execution.ExecutionLimits
import com.jjt.ingsis.printscript.execution.ExecutionRegistry
import com.jjt.ingsis.printscript.processor.ProcessorCapacity
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ExecutionLimits::class)
class ExecutionConfiguration {
    @Bean
    fun processorCapacity(limits: ExecutionLimits): ProcessorCapacity = ProcessorCapacity(limits.activeGlobal)

    @Bean(destroyMethod = "close")
    fun executionRegistry(
        limits: ExecutionLimits,
        codec: ExecutionEventCodec,
        capacity: ProcessorCapacity,
    ): ExecutionRegistry = ExecutionRegistry(limits, capacity) { codec.encode(it).size + 1 }
}
