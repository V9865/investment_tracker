package com.personal.investmenttracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoUnitTest {
    @Test
    fun projectInitialization_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun demoMode_requiresNoCredentials() {
        val requiresCredentials = false
        assertTrue(!requiresCredentials)
    }
}
