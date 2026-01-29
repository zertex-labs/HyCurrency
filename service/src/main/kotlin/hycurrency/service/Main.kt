package hycurrency.service

import hycurrency.cache.ValkeyCacheClient
import hycurrency.service.config.ConfigLoader
import hycurrency.service.config.MapCurrencyConfigProvider
import hycurrency.service.database.DatabaseFactory
import hycurrency.service.idempotency.PostgresIdempotencyStore
import hycurrency.service.jobs.IdempotencyCleanupJob
import hycurrency.service.repository.PostgresCurrencyRepository
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("hycurrency.service.Main")

fun main() {
    logger.info("Starting HyCurrency Service...")

    val config = ConfigLoader.loadFromEnv()
    logger.info("Loaded configuration: port={}, currencies={}", 
        config.server.port, 
        config.currencies.map { it.id }
    )

    val currencyConfig = MapCurrencyConfigProvider(config.currencies)

    val dataSource = DatabaseFactory.create(config.database)
    logger.info("Database connection pool initialized")

    val cacheClient = ValkeyCacheClient(config.cache.redisUrl)
    logger.info("Cache client initialized: {}", config.cache.redisUrl)

    val currencyRepository = PostgresCurrencyRepository(dataSource, currencyConfig)
    val idempotencyStore = PostgresIdempotencyStore(dataSource, config.idempotency.ttl)

    val cleanupJob = IdempotencyCleanupJob(idempotencyStore, config.idempotency.cleanupInterval)
    cleanupJob.start()

    val currencyService = CurrencyServiceImpl(
        config = config,
        currencyConfig = currencyConfig,
        repository = currencyRepository,
        idempotencyStore = idempotencyStore,
        cacheClient = cacheClient
    )

    val server = ServerBuilder
        .forPort(config.server.port)
        .addService(currencyService)
        .apply {
            if (config.server.enableReflection) {
                logger.info("gRPC reflection enabled")
                addService(ProtoReflectionService.newInstance())
            }
        }
        .build()

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down HyCurrency Service...")
        cleanupJob.stop()
        server.shutdown()
        cacheClient.close()
        dataSource.close()
        logger.info("Shutdown complete")
    })

    server.start()
    logger.info("HyCurrency Service started on port {}", config.server.port)

    // Block until shutdown
    server.awaitTermination()
}
