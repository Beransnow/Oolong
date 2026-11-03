package oolong.core.network.json

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EmptyArraysTest {
    @Test
    fun `treats empty arrays as absent at any depth`() {
        val element = Json.parseToJsonElement("""{"info":[],"data":{"toast":[],"list":[{"error":[]}]}}""")

        assertEquals(
            Json.parseToJsonElement("""{"info":null,"data":{"toast":null,"list":[{"error":null}]}}"""),
            element.withoutEmptyArrays(),
        )
    }
}
