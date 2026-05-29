package com.votingapp

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ServerTest {

    @Test
    fun `test root endpoint`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            module()
        }
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

}
