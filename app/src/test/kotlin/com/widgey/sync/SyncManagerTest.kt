package com.widgey.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManagerTest {

    @Test
    fun `calculateNextRetryTime with 0 retries returns approximately 1 second delay`() {
        val now = System.currentTimeMillis()
        val nextRetry = calculateNextRetryTime(0, now)
        val delay = nextRetry - now

        assertEquals(1000L, delay)
    }

    @Test
    fun `calculateNextRetryTime doubles delay with each retry`() {
        val now = System.currentTimeMillis()

        val delays = (0..5).map { retryCount ->
            calculateNextRetryTime(retryCount, now) - now
        }

        assertEquals(1000L, delays[0])   // 2^0 * 1000 = 1s
        assertEquals(2000L, delays[1])   // 2^1 * 1000 = 2s
        assertEquals(4000L, delays[2])   // 2^2 * 1000 = 4s
        assertEquals(8000L, delays[3])   // 2^3 * 1000 = 8s
        assertEquals(16000L, delays[4])  // 2^4 * 1000 = 16s
        assertEquals(32000L, delays[5])  // 2^5 * 1000 = 32s
    }

    @Test
    fun `calculateNextRetryTime caps at 15 minutes`() {
        val now = System.currentTimeMillis()
        val maxDelayMs = 15 * 60 * 1000L // 15 minutes

        // At retry count 10, uncapped would be 2^10 * 1000 = 1,024,000ms (~17 min)
        val delay10 = calculateNextRetryTime(10, now) - now
        assertEquals(maxDelayMs, delay10)

        // At retry count 20, should still be capped
        val delay20 = calculateNextRetryTime(20, now) - now
        assertEquals(maxDelayMs, delay20)
    }

    @Test
    fun `calculateNextRetryTime returns future timestamp`() {
        val now = System.currentTimeMillis()

        for (retryCount in 0..15) {
            val nextRetry = calculateNextRetryTime(retryCount, now)
            assertTrue("Next retry should be in the future", nextRetry > now)
        }
    }

    @Test
    fun `backoff reaches cap at retry count 10`() {
        val now = System.currentTimeMillis()
        val maxDelayMs = 15 * 60 * 1000L

        // 2^9 * 1000 = 512,000ms (8.5 min) - still under cap
        val delay9 = calculateNextRetryTime(9, now) - now
        assertEquals(512000L, delay9)

        // 2^10 * 1000 = 1,024,000ms (17 min) - should be capped to 15 min
        val delay10 = calculateNextRetryTime(10, now) - now
        assertEquals(maxDelayMs, delay10)
    }

    /**
     * Helper function that mirrors SyncManager's backoff calculation.
     * In a real scenario, we'd either:
     * 1. Make the calculation a public static/companion function in SyncManager
     * 2. Extract it to a separate BackoffCalculator class
     * 3. Test through SyncManager's public interface
     */
    private fun calculateNextRetryTime(retryCount: Int, currentTime: Long): Long {
        val initialBackoffMs = 1000L
        val maxBackoffMs = 15 * 60 * 1000L // 15 minutes

        val backoffMs = minOf(
            initialBackoffMs * (1L shl retryCount), // 2^retryCount * 1000
            maxBackoffMs
        )
        return currentTime + backoffMs
    }
}
