package com.jjt.ingsis.printscript.processor

import com.jjt.ingsis.printscript.execution.ExecutionLimits
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProcessorConfiguration {
    @Bean
    fun processorArtifact(): ProcessorArtifact = RunningJar(System.getProperty("java.class.path").orEmpty())

    @Bean
    fun processorIdentity(
        artifact: ProcessorArtifact,
        limits: ExecutionLimits,
    ): ProcessorIdentity = ProcessorIdentity(artifact, limits)

    @Bean
    fun processorRevisionFilter(identity: ProcessorIdentity): FilterRegistrationBean<ProcessorRevisionFilter> =
        FilterRegistrationBean(ProcessorRevisionFilter(identity))
}
