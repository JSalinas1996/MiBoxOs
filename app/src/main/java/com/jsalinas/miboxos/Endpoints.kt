package com.jsalinas.miboxos

/** URLs de servicios externos; los tests las apuntan a servidores falsos locales. */
object Endpoints {
    @Volatile var gemini = "https://generativelanguage.googleapis.com"
    @Volatile var hueDiscovery = "https://discovery.meethue.com/"
}
