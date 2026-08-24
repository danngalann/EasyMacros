package com.danngalann.easymacros.data.api

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkFailureLoggingInterceptorTest {
    @Test fun `redacts query values from network failure logs`() {
        val request = Request.Builder()
            .url("http://192.168.0.30:3000/dashboard?cursor=opaque-secret")
            .get()
            .build()

        assertEquals("Network request failed: GET 192.168.0.30/dashboard", request.networkFailureMessage())
    }
}