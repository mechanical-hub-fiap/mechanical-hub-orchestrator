package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.UncategorizedMongoDbException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.IndexOperations

class MongoIndexInitializerTest {
    @Test
    fun `given an index that cannot be created when initializing then startup fails`() {
        val indexOperations = mockk<IndexOperations>()
        every { indexOperations.createIndex(any()) } throws UncategorizedMongoDbException("indice conflitante", null)
        val mongoTemplate = mockk<MongoTemplate>()
        every { mongoTemplate.collectionExists(any<String>()) } returns true
        every { mongoTemplate.indexOps(any<String>()) } returns indexOperations
        val initializer = MongoIndexInitializer(mongoTemplate)

        val initialization = { initializer.afterPropertiesSet() }

        assertThatThrownBy(initialization).isInstanceOf(UncategorizedMongoDbException::class.java)
    }
}
