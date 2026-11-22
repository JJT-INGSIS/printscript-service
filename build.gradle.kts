plugins {
    id("jjt.spring-service") version "0.3.0"
}

group = "com.jjt.ingsis"
version = "0.0.1-SNAPSHOT"

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/jjt-ingsis/printscript")

        credentials {
            username = providers.environmentVariable("GITHUB_ACTOR").orNull
            password = providers.environmentVariable("GITHUB_TOKEN").orNull
        }

        content {
            includeGroup("io.github.jjt-ingsis.printscript")
        }
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.newrelic.agent.java:newrelic-api:9.5.0")
    implementation("io.github.jjt-ingsis.printscript:printscript-v1:1.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.testcontainers:testcontainers")
}

tasks.test {
    inputs.file("docs/validation.md")
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}
