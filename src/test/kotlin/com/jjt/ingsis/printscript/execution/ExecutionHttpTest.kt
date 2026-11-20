package com.jjt.ingsis.printscript.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExecutionHttpTest(
    @Value("\${local.server.port}") private val port: Int,
    @Autowired private val mapper: ObjectMapper,
) {
    private val client = HttpClient.newHttpClient()

    @Test
    fun `real HTTP delivers output while interpreter waits for input then completes`() {
        val started =
            post(
                "/executions",
                mapper.writeValueAsString(
                    mapOf(
                        "code" to "println(\"antes\"); let a: string = readInput(\"?\"); println(a);",
                        "version" to "1.1",
                        "mode" to "INTERACTIVE",
                    ),
                ),
            )
        assertThat(started.statusCode()).isEqualTo(201)
        val id = mapper.readTree(started.body()).path("executionId").asString()
        val response = client.send(request("/executions/$id/events"), HttpResponse.BodyHandlers.ofInputStream())
        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/x-ndjson")
        response.body().bufferedReader().use { reader ->
            assertThat(mapper.readTree(reader.readLine()).path("type").asString()).isEqualTo("started")
            assertThat(
                mapper
                    .readTree(reader.readLine())
                    .path("data")
                    .path("text")
                    .asString(),
            ).isEqualTo("antes")
            val input = mapper.readTree(reader.readLine())
            assertThat(input.path("type").asString()).isEqualTo("input-required")
            val requestId = input.path("data").path("requestId").asString()
            val inputBody = """{"requestId":"$requestId","value":""}"""
            assertThat(post("/executions/$id/inputs", inputBody).statusCode()).isEqualTo(200)
            assertThat(post("/executions/$id/inputs", inputBody).statusCode()).isEqualTo(200)
            val rest = reader.lineSequence().map { mapper.readTree(it) }.toList()
            assertThat(rest.map { it.path("type").asString() }).containsExactly("input-accepted", "output", "terminal")
            assertThat(
                rest
                    .last()
                    .path("data")
                    .path("outcome")
                    .asString(),
            ).isEqualTo("COMPLETED")
        }
        assertThat(post("/executions/$id/cancel", "{}").statusCode()).isEqualTo(202)
    }

    @Test
    fun `strict HTTP requests reject missing unknown and inappropriate fields`() {
        val invalid =
            listOf(
                "{}",
                "null",
                """{"code":"","version":"1.0","mode":"TEST"}""",
                """{"code":"","version":"1.0","mode":"TEST","inputs":[null]}""",
                """{"code":"","version":"1.1","mode":"INTERACTIVE","inputs":[]}""",
                """{"code":"","version":"1.0","mode":"TEST","inputs":[],"actorId":"fake"}""",
            )
        for (body in invalid) assertThat(post("/executions", body).statusCode()).isEqualTo(400)
        val unsupported = post("/executions", """{"code":"","version":"2.0","mode":"TEST","inputs":[]}""")
        assertThat(unsupported.statusCode()).isEqualTo(422)
        assertThat(unsupported.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json")
        assertThat(mapper.readTree(unsupported.body()).path("supportedVersions").size()).isEqualTo(2)
    }

    @Test
    fun `missing session and invalid cursor are not stream successes`() {
        val missing = client.send(request("/executions/${UUID.randomUUID()}"), HttpResponse.BodyHandlers.ofString())
        assertThat(missing.statusCode()).isEqualTo(404)
        val started = post("/executions", """{"code":"","version":"1.0","mode":"TEST","inputs":[]}""")
        val id = mapper.readTree(started.body()).path("executionId").asString()
        val invalid = client.send(request("/executions/$id/events?after=-1"), HttpResponse.BodyHandlers.ofString())
        assertThat(invalid.statusCode()).isEqualTo(400)
        assertThat(mapper.readTree(invalid.body()).path("type").asString()).endsWith(":invalid-cursor")
    }

    private fun request(path: String): HttpRequest =
        HttpRequest
            .newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

    private fun post(
        path: String,
        body: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:$port$path"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
}
