package com.expedia.graphql.dataFetchers

import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.exceptions.GraphQLKotlinException
import com.expedia.graphql.getTestSchemaConfigWithHooks
import com.expedia.graphql.hooks.DataFetcherExecutionPredicate
import com.expedia.graphql.hooks.SchemaGeneratorHooks
import com.expedia.graphql.toSchema
import graphql.ExceptionWhileDataFetching
import graphql.GraphQL
import graphql.schema.DataFetchingEnvironment
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KParameter
import kotlin.test.assertEquals

class DataFetchPredicateTests {

    @Test
    fun `A datafetcher execution is stopped if the predicate test is false`() {
        val config = getTestSchemaConfigWithHooks(PredicateHooks())
        val schema = toSchema(
                listOf(TopLevelObject(QueryWithValidations())),
                config = config
        )
        val graphQL = GraphQL.newGraphQL(schema).build()
        val result = graphQL.execute("{ greaterThan2Times10(greaterThan2: 1) }")
        val graphQLError = result.errors[0]

        assertEquals("DataFetchingException", graphQLError.errorType.name)
        val exception = (graphQLError as? ExceptionWhileDataFetching)?.exception
        assertEquals("The datafetcher cannot be executed due to: [Error(message=greaterThan2 is actually 1)]", exception?.message)
        assertTrue(exception is GraphQLKotlinException)
    }

    @Test
    fun `A datafetcher execution is stopped if the predicate test is false with complex argument under test`() {
        val config = getTestSchemaConfigWithHooks(PredicateHooks())
        val schema = toSchema(
                listOf(TopLevelObject(QueryWithValidations())),
                config = config
        )
        val graphQL = GraphQL.newGraphQL(schema).build()
        val result = graphQL.execute("{ complexPredicate(person: { age: 33, name: \"Alice\"}) }")
        val graphQLError = result.errors[0]

        assertEquals("DataFetchingException", graphQLError.errorType.name)
        val exception = (graphQLError as? ExceptionWhileDataFetching)?.exception
        assertEquals("The datafetcher cannot be executed due to: [Error(message=Not the right age), Error(message=Not the right name)]", exception?.message)
        assertTrue(exception is GraphQLKotlinException)
    }
}

class QueryWithValidations {
    fun greaterThan2Times10(greaterThan2: Int): Int = greaterThan2 * 10
    fun complexPredicate(person: Person) = person.toString()
}

data class Person(val name: String, val age: Int)

class PredicateHooks : SchemaGeneratorHooks {
    override val dataFetcherExecutionPredicate: DataFetcherExecutionPredicate? = TestDataFetcherPredicate()
}

class TestDataFetcherPredicate : DataFetcherExecutionPredicate() {

    override fun <T> evaluate(value: T, parameter: KParameter, environment: DataFetchingEnvironment): Any = when {
        parameter.name == "greaterThan2" && value is Int && value <= 2 -> listOf(Error("greaterThan2 is actually $value"))
        value is Person -> {
            val errors = mutableListOf<Error>()
            if (value.age < 42) errors.add(Error("Not the right age"))
            if (value.name != "Bob") errors.add(Error("Not the right name"))
            errors
        }
        else -> emptyList()
    }

    override fun test(evaluationResult: Any): Boolean = (evaluationResult as? List<*>)?.isEmpty() ?: true

    @Throws(GraphQLKotlinException::class)
    override fun onFailure(evaluationResult: Any, parameter: KParameter, environment: DataFetchingEnvironment): Nothing {
        throw GraphQLKotlinException("The datafetcher cannot be executed due to: $evaluationResult")
    }

    data class Error(val message: String)
}
