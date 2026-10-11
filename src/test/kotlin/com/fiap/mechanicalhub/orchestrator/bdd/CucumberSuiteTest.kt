package com.fiap.mechanicalhub.orchestrator.bdd

import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/** BDD do fluxo completo da saga; a configuracao do Cucumber esta em `junit-platform.properties`. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
class CucumberSuiteTest
