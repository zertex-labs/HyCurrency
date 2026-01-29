package hycurrency.service.jobs

import hycurrency.service.idempotency.IdempotencyStore
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration

/**
 * Background job that periodically cleans up expired idempotency records.
 * 
 * Runs on a fixed interval, calling [IdempotencyStore.cleanup] to remove
 * records older than the configured TTL(set by IDEMPOTENCY_TTL_HOURS env variable).
 */
class IdempotencyCleanupJob(
    private val idempotencyStore: IdempotencyStore,
    private val interval: Duration,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val logger = LoggerFactory.getLogger(IdempotencyCleanupJob::class.java)
    private var job: Job? = null

    /**
     * Start the cleanup job. The job will run immediately, then repeat at the configured interval.
     */
    fun start() {
        if (job?.isActive == true) {
            logger.warn("Cleanup job already running")
            return
        }

        logger.info("Starting idempotency cleanup job with interval: {}", interval)
        
        job = scope.launch {
            while (isActive) {
                try {
                    val deleted = idempotencyStore.cleanup()
                    if (deleted > 0) {
                        logger.info("Cleaned up {} expired idempotency records", deleted)
                    } else {
                        logger.debug("No expired idempotency records to clean up")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Error during idempotency cleanup", e)
                }
                
                delay(interval)
            }
        }
    }

    /**
     * Stop the cleanup job gracefully.
     */
    fun stop() {
        logger.info("Stopping idempotency cleanup job")
        job?.cancel()
        job = null
    }

    /**
     * Check if the cleanup job is currently running.
     */
    val isRunning: Boolean
        get() = job?.isActive == true
}
