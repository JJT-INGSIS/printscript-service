plugins {
    id("jjt.spring-service") version "0.2.0"
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
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.github.jjt-ingsis.printscript:printscript-v1:1.1.0")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}

tasks.test {
    inputs.dir("docs")
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}
