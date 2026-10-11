package com.fiap.mechanicalhub.orchestrator.infrastructure.observability

import org.slf4j.MDC

/** Chaves de correlacao do log JSON (logback-spring.xml). */
object LogContext {
    const val ORDER_ID = "orderId"
    const val TRANSACTION_ID = "transactionId"
    const val EVENT_ID = "eventId"
    const val CURRENT_STEP = "currentStep"

    /** Executa `block` com as chaves no MDC e remove todas ao final (valores nulos nao sao colocados). */
    fun <T> with(
        vararg entries: Pair<String, Any?>,
        block: () -> T,
    ): T {
        entries.forEach { (key, value) -> value?.let { MDC.put(key, it.toString()) } }
        return try {
            block()
        } finally {
            entries.forEach { (key, _) -> MDC.remove(key) }
        }
    }

    fun put(
        key: String,
        value: Any?,
    ) {
        value?.let { MDC.put(key, it.toString()) }
    }
}
