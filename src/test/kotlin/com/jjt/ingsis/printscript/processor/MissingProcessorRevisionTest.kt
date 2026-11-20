package com.jjt.ingsis.printscript.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MissingProcessorRevisionTest(
    @Value("\${local.server.port}") private val port: Int,
) {
    @Test
    fun `outside a jar the service answers without inventing a revision`() {
        val response =
            HttpClient.newHttpClient().send(
                HttpRequest
                    .newBuilder(URI("http://127.0.0.1:$port/validate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"code":"println(1);","version":"1.1"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue(PROCESSOR_REVISION_HEADER)).isEmpty()
    }
}
