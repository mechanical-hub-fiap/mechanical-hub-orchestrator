package com.fiap.mechanicalhub.orchestrator.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuracao propria do servico, preenchida por variaveis de ambiente
 * (application.yml). Nenhum valor sensivel: as credenciais AWS vem da LabRole.
 */
@ConfigurationProperties(prefix = "mechanical-hub.orchestrator")
data class OrchestratorProperties(
    val queues: Queues,
) {
    data class Queues(
        val orchestratorEvents: String,
        val ordersCommands: String,
        val stockCommands: String,
        val billingCommands: String,
    )
}
