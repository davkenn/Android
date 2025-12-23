package protect.card_locker.debug

import kotlinx.coroutines.flow.Flow

/**
 * NO-OP Flow Monitor Stub (Standard/Production Builds)
 *
 * This is a compile-time stub that does nothing. It exists so that code
 * using `.monitor()` can compile in standard builds, but the monitoring
 * functionality is completely removed at compile time (zero runtime overhead).
 *
 * The real implementation with JSON logging lives in app/src/recording/
 * and is ONLY compiled into recording build variants.
 *
 * This architecture ensures:
 * 1. Production builds have zero monitoring overhead
 * 2. Developers can't accidentally ship monitoring code
 * 3. The same source code compiles for both standard and recording builds
 *
 * Example usage (same in both flavors):
 *   viewModel.cardState
 *       .monitor("cardState")  // Does nothing in standard, logs in recording
 *       .collectLatest { state -> /* ... */ }
 */

/**
 * No-op extension function - returns the flow unchanged
 *
 * @param tag Ignored in standard builds (kept for API compatibility)
 * @return The original flow without any modifications
 */
fun <T> Flow<T>.monitor(tag: String): Flow<T> {
    // No-op: Just return the original flow
    // Kotlin optimizer will inline this away completely
    return this
}
