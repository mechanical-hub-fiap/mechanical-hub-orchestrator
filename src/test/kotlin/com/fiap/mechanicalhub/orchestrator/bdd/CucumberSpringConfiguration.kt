package com.fiap.mechanicalhub.orchestrator.bdd

import com.fiap.mechanicalhub.orchestrator.TestcontainersConfiguration
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/** Sobe a aplicacao inteira (MongoDB replica set + LocalStack) uma vez para todos os cenarios. */
@CucumberContextConfiguration
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CucumberSpringConfiguration
