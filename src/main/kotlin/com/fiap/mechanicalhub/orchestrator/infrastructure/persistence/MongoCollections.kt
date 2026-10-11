package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence

/** Nomes das colecoes do servico (secao 4 da spec). */
object MongoCollections {
    const val TRANSACTION_REGISTER = "transaction_register"
    const val TRANSACTION_HISTORY = "transaction_history"
    const val OUTBOX_EVENTS = "outbox_events"
    const val SHEDLOCK = "shedLock"
}
