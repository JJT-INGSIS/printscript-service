package com.jjt.ingsis.printscript.execution.http

import com.jjt.ingsis.printscript.execution.ExecutionLimits
import com.jjt.ingsis.printscript.execution.ExecutionRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ExecutionLimits::class)
class ExecutionConfiguration {
    @Bean(destroyMethod = "close")
    fun executionRegistry(
        limits: ExecutionLimits,
        codec: ExecutionEventCodec,
    ): ExecutionRegistry = ExecutionRegistry(limits) { codec.encode(it).size + 1 }
}
