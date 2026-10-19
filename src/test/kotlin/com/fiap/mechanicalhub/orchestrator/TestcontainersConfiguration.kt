package com.fiap.mechanicalhub.orchestrator

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.localstack.LocalStackContainer
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun mongoDbContainer(): MongoDBContainer =
        // Replica set de 1 no, como em todos os ambientes (ADR-0004): sem ele nao ha transacoes.
        MongoDBContainer(DockerImageName.parse("mongo:7")).withReplicaSet()

    /** Mesmas filas FIFO + DLQs do compose.yaml, criadas antes do listener subir. */
    @Bean
    @ServiceConnection
    fun localStackContainer(): LocalStackContainer =
        LocalStackContainer(DockerImageName.parse("localstack/localstack:4.4"))
            .withServices("sqs")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/localstack/init-sqs.sh", OWNER_EXECUTE), "/opt/init-sqs.sh")
            .apply {
                start()
                val result = execInContainer("bash", "/opt/init-sqs.sh")
                check(result.exitCode == 0) { "Falha ao criar as filas: ${result.stderr}" }
            }

    private companion object {
        const val OWNER_EXECUTE = 0b111_101_101
    }
}
