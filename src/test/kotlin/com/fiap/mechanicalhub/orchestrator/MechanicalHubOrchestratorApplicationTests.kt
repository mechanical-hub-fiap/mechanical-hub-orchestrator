package com.fiap.mechanicalhub.orchestrator

import com.fiap.mechanicalhub.orchestrator.infrastructure.config.OrchestratorProperties
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class MechanicalHubOrchestratorApplicationTests {
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Autowired
    private lateinit var orchestratorProperties: OrchestratorProperties

    @Test
    fun `given the application when the context starts then it loads`() {
        // Falha se o contexto Spring nao subir.
    }

    @Test
    fun `given the test mongo when asking hello then it is the primary of a replica set`() {
        val hello = mongoTemplate.executeCommand(Document("hello", 1))

        assertThat(hello.getString("setName")).isNotBlank()
        assertThat(hello.getBoolean("isWritablePrimary")).isTrue()
    }

    @Test
    fun `given no queue env vars when binding properties then the four fifo queues use default names`() {
        val queues = orchestratorProperties.queues

        assertThat(queues.orchestratorEvents).isEqualTo("mechanical-hub-orchestrator-events.fifo")
        assertThat(queues.ordersCommands).isEqualTo("mechanical-hub-orders-commands.fifo")
        assertThat(queues.stockCommands).isEqualTo("mechanical-hub-stock-commands.fifo")
        assertThat(queues.billingCommands).isEqualTo("mechanical-hub-billing-commands.fifo")
    }
}
