package com.jjt.ingsis.printscript.formatting

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(FormattingLimits::class)
class FormattingConfiguration
