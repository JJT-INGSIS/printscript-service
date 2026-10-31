package com.jjt.ingsis.printscript.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ProcessorRevisionHttpTest.KnownArtifact::class)
class ProcessorRevisionHttpTest(
    @Value("\${local.server.port}") private val port: Int,
    @Autowired private val identity: ProcessorIdentity,
    @Autowired private val mapper: ObjectMapper,
) {
    private val client = HttpClient.newHttpClient()

    @Test
    fun `every validation answer carries the validation revision even when it is an error`() {
        val valid = post("/validate", """{"code":"println(1);","version":"1.1"}""")
        val unsupported = post("/validate", """{"code":"println(1);","version":"9.9"}""")

        assertThat(valid.statusCode()).isEqualTo(200)
        assertThat(revision(valid)).isEqualTo(identity.validation).isNotNull()
        assertThat(unsupported.statusCode()).isEqualTo(422)
        assertThat(revision(unsupported)).isEqualTo(identity.validation)
    }

    @Test
    fun `every execution answer carries the execution revision`() {
        val started = post("/executions", """{"code":"println(1);","version":"1.1","mode":"TEST","inputs":[]}""")
        val id = mapper.readTree(started.body()).path("executionId").asString()
        val events = get("/executions/$id/events?after=0")
        val missing = get("/executions/${UUID.randomUUID()}")

        assertThat(started.statusCode()).isEqualTo(201)
        assertThat(revision(started)).isEqualTo(identity.execution).isNotEqualTo(identity.validation)
        assertThat(events.statusCode()).isEqualTo(200)
        assertThat(revision(events)).isEqualTo(identity.execution)
        assertThat(missing.statusCode()).isEqualTo(404)
        assertThat(revision(missing)).isEqualTo(identity.execution)
    }

    @Test
    fun `the processor page lists the artifact and the revision of each capability`() {
        val page = mapper.readTree(get("/processor").body())

        assertThat(page.path("artifact").asString()).isEqualTo("sha256:" + "c".repeat(64))
        assertThat(page.path("capabilities").path("validate").asString()).isEqualTo(identity.validation)
        assertThat(page.path("capabilities").path("execute").asString()).isEqualTo(identity.execution)
    }

    private fun revision(response: HttpResponse<String>): String? =
        response.headers().firstValue(PROCESSOR_REVISION_HEADER).orElse(null)

    private fun get(path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun post(
        path: String,
        body: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(URI("http://127.0.0.1:$port$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @TestConfiguration
    class KnownArtifact {
        @Bean
        @Primary
        fun fixedArtifact(): ProcessorArtifact = ProcessorArtifact { "c".repeat(64) }
    }
}
