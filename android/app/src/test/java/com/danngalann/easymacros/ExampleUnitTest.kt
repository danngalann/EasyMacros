package com.danngalann.easymacros

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun applicationIdUsesTheConfiguredPackage() {
        assertEquals("com.danngalann.easymacros", BuildConfig.APPLICATION_ID)
    }
}