package com.fiap.mechanicalhub.orchestrator.infrastructure.config

import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.TransactionOptions
import com.mongodb.WriteConcern
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager

/**
 * Pre-requisitos da secao 4 da spec (ADR-0004): `writeConcern: majority`,
 * `readConcern: majority`, `readPreference: primary` no cliente e transacoes
 * multi-documento em `snapshot` pelo [MongoTransactionManager].
 *
 * Os valores sao fixados aqui (e nao na URI) para valerem em qualquer ambiente,
 * independentemente da `MONGODB_URI` recebida.
 */
@Configuration(proxyBeanMethods = false)
class MongoConfig {
    @Bean
    fun mongoConsistencyCustomizer(): MongoClientSettingsBuilderCustomizer =
        MongoClientSettingsBuilderCustomizer { builder ->
            builder
                .writeConcern(WriteConcern.MAJORITY)
                .readConcern(ReadConcern.MAJORITY)
                .readPreference(ReadPreference.primary())
        }

    @Bean
    fun transactionManager(databaseFactory: MongoDatabaseFactory): MongoTransactionManager =
        MongoTransactionManager(databaseFactory, sagaTransactionOptions())

    companion object {
        fun sagaTransactionOptions(): TransactionOptions =
            TransactionOptions
                .builder()
                .readConcern(ReadConcern.SNAPSHOT)
                .writeConcern(WriteConcern.MAJORITY)
                .readPreference(ReadPreference.primary())
                .build()
    }
}
