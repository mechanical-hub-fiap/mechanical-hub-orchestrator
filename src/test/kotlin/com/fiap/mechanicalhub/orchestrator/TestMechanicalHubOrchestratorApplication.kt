package com.fiap.mechanicalhub.orchestrator

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<MechanicalHubOrchestratorApplication>().with(TestcontainersConfiguration::class).run(*args)
}
