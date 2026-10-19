package com.fiap.mechanicalhub.orchestrator.infrastructure.config

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class SqsConfig {
    /**
     * O corpo ja e o envelope JSON. Sem o header de tipo Java: os message
     * attributes (limite de 10) ficam livres para o contexto de trace.
     */
    @Bean
    fun sqsTemplate(sqsAsyncClient: SqsAsyncClient): SqsTemplate =
        SqsTemplate
            .builder()
            .sqsAsyncClient(sqsAsyncClient)
            .configureDefaultConverter { it.doNotSendPayloadTypeHeader() }
            .build()

    /**
     * Consumo de `mechanical-hub-orchestrator-events.fifo` (spec Etapa 4,
     * failure_handling.md §2.2): confirmacao manual (a mensagem so e apagada
     * depois do `commitTransaction`), long polling de 20 s, visibility timeout
     * de 30 s e uma mensagem por poll, para que uma mensagem em retry nunca
     * deixe a seguinte do mesmo grupo (`orderId`) passar na frente.
     */
    @Bean
    fun defaultSqsListenerContainerFactory(sqsAsyncClient: SqsAsyncClient): SqsMessageListenerContainerFactory<Any> =
        SqsMessageListenerContainerFactory
            .builder<Any>()
            .sqsAsyncClient(sqsAsyncClient)
            .configure { options ->
                options
                    .acknowledgementMode(AcknowledgementMode.MANUAL)
                    .pollTimeout(Duration.ofSeconds(LONG_POLLING_SECONDS))
                    .messageVisibility(Duration.ofSeconds(VISIBILITY_TIMEOUT_SECONDS))
                    .maxMessagesPerPoll(1)
                    .messageSystemAttributeNames(listOf(MessageSystemAttributeName.ALL))
            }.build()

    private companion object {
        const val LONG_POLLING_SECONDS = 20L
        const val VISIBILITY_TIMEOUT_SECONDS = 30L
    }
}
