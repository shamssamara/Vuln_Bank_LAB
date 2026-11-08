package com.vulnbank.server

import kotlin.test.Test
import kotlin.test.assertTrue

class LabContractTest {
    @Test fun demoPayloadHasClassicBooleanExpression() {
        val payload = "' OR 1=1 --"
        val sql = "SELECT id FROM users WHERE username = '$payload' AND password = 'anything'"
        assertTrue(sql.contains("OR 1=1 --"))
        assertTrue(sql.substringAfter("--").contains("password"))
    }
}
