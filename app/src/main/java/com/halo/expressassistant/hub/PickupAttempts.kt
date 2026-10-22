package com.halo.expressassistant.hub

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object PickupAttempts {
    /** A matched shopping order with no code must still try the waybill channel. */
    suspend fun resolve(attempts: List<suspend () -> String?>): String? {
        for (attempt in attempts) {
            val code = try { attempt() } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); null }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (!code.isNullOrBlank()) return code
        }
        return null
    }
}
