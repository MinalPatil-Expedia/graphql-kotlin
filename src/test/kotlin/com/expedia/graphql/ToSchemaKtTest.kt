package com.expedia.graphql

import org.junit.jupiter.api.Test

internal class ToSchemaKtTest {

    internal class TestClass

    @Test
    fun `valid schema`() {
        val queries = listOf(TopLevelObject(TestClass()))
        toSchema(queries = queries, config = testSchemaConfig)
    }
}
