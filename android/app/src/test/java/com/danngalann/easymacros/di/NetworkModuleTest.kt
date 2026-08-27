package com.danngalann.easymacros.di

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun `tracking client allows the backend advanced-model window`() {
        val client = withRationAnalysisTimeout(OkHttpClient())

        assertEquals(120_000, client.callTimeoutMillis)
        assertEquals(100_000, client.readTimeoutMillis)
        assertEquals(60_000, client.writeTimeoutMillis)
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(120, client.callTimeoutMillis.toLong() / TimeUnit.SECONDS.toMillis(1))
    }
}
