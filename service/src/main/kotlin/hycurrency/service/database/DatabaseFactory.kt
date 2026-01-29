package hycurrency.service.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import hycurrency.service.config.DatabaseConfig

/**
 * Factory for creating database connections using HikariCP.
 */
object DatabaseFactory {

    /**
     * Create a HikariCP DataSource from configuration.
     */
    fun create(config: DatabaseConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = config.maxPoolSize
            minimumIdle = config.minIdle

            // Connection pool settings
            connectionTimeout = 10_000 // 10 seconds
            idleTimeout = 600_000 // 10 minutes
            maxLifetime = 1_800_000 // 30 minutes

            // PostgreSQL optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        return HikariDataSource(hikariConfig)
    }
}
