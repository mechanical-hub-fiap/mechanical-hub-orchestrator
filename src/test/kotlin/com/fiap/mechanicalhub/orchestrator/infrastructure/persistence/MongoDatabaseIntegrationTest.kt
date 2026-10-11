package com.fiap.mechanicalhub.orchestrator.infrastructure.persistence

import com.fiap.mechanicalhub.orchestrator.TestcontainersConfiguration
import com.mongodb.ReadConcern
import com.mongodb.ReadPreference
import com.mongodb.WriteConcern
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bson.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.MongoTransactionOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class MongoDatabaseIntegrationTest {
    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var lockProvider: LockProvider

    @Test
    fun `given the mongo client when reading its settings then concerns and read preference follow the spec`() {
        val database = mongoTemplate.db

        assertThat(database.writeConcern).isEqualTo(WriteConcern.MAJORITY)
        assertThat(database.readConcern).isEqualTo(ReadConcern.MAJORITY)
        assertThat(database.readPreference).isEqualTo(ReadPreference.primary())
    }

    @Test
    fun `given the context when resolving the transaction manager then it is a mongo manager in snapshot`() {
        val options = ReflectionTestUtils.getField(transactionManager, "options") as MongoTransactionOptions

        assertThat(transactionManager).isInstanceOf(MongoTransactionManager::class.java)
        assertThat(options.readConcern).isEqualTo(ReadConcern.SNAPSHOT)
        assertThat(options.writeConcern).isEqualTo(WriteConcern.MAJORITY)
    }

    @Test
    fun `given the application started when listing collections then the four collections exist`() {
        val collections = mongoTemplate.collectionNames

        assertThat(collections).containsAll(MongoIndexInitializer.COLLECTIONS)
    }

    @Test
    fun `given the application started when listing indexes then all spec indexes exist with uniqueness`() {
        val indexesByCollection =
            MongoIndexInitializer.INDEXES.keys.associateWith { collection ->
                mongoTemplate.indexOps(collection).indexInfo.associate { it.name to it.isUnique }
            }

        assertThat(indexesByCollection[MongoCollections.TRANSACTION_REGISTER]).containsAllEntriesOf(
            mapOf(
                "ux_transaction_register_order_id" to true,
                "ix_transaction_register_status_step_updated_at" to false,
            ),
        )
        assertThat(indexesByCollection[MongoCollections.TRANSACTION_HISTORY]).containsAllEntriesOf(
            mapOf(
                "ux_transaction_history_message_key" to true,
                "ix_transaction_history_order_id_seq" to false,
                "ix_transaction_history_message_type_received_at" to false,
            ),
        )
        assertThat(indexesByCollection[MongoCollections.OUTBOX_EVENTS]).containsAllEntriesOf(
            mapOf(
                "ix_outbox_events_status_created_at" to false,
                "ux_outbox_events_order_id_seq" to true,
            ),
        )
    }

    @Test
    fun `given a saga for an order when inserting another saga for the same order then it is rejected`() {
        val orderId = UUID.randomUUID().toString()
        mongoTemplate.insert(
            Document(mapOf("_id" to UUID.randomUUID().toString(), "orderId" to orderId)),
            MongoCollections.TRANSACTION_REGISTER,
        )

        val duplicate = Document(mapOf("_id" to UUID.randomUUID().toString(), "orderId" to orderId))

        assertThatThrownBy { mongoTemplate.insert(duplicate, MongoCollections.TRANSACTION_REGISTER) }
            .isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `given a failing transaction when it rolls back then no document from it is persisted`() {
        val messageKey = UUID.randomUUID().toString()
        val transactionTemplate = TransactionTemplate(transactionManager)

        runCatching {
            transactionTemplate.executeWithoutResult {
                mongoTemplate.insert(Document("messageKey", messageKey), MongoCollections.TRANSACTION_HISTORY)
                error("forca rollback")
            }
        }

        val persisted = mongoTemplate.getCollection(MongoCollections.TRANSACTION_HISTORY).countDocuments(Document("messageKey", messageKey))
        assertThat(persisted).isZero()
    }

    @Test
    fun `given a held lease when another instance tries to lock then it is refused`() {
        val lockName = "relay-${UUID.randomUUID()}"
        val configuration = LockConfiguration(Instant.now(), lockName, Duration.ofSeconds(30), Duration.ZERO)
        val firstLock = lockProvider.lock(configuration)

        val secondLock = lockProvider.lock(configuration)

        assertThat(firstLock).isPresent
        assertThat(secondLock).isEmpty
        assertThat(mongoTemplate.getCollection(MongoCollections.SHEDLOCK).countDocuments(Document("_id", lockName))).isEqualTo(1)
    }
}
