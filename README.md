# HyCurrency

Currency microservice for Hytale server networks. Manages player balances (coins, gems, etc.) via gRPC. Made to be as generic as possible, every currency is defined in `currencies.json`.

## Architecture

Stores balances in PostgreSQL with Valkey caching. Plugins use the client JAR to communicate with the service over gRPC.

## Quick Start

```bash
# Build the service JAR, required to build the service image
./gradlew :service:shadowJar

# Optional: build the service container(will be done by the next step, do this just to verify if the container builds fine)
docker build -t hycurrency-service .

# Start everything(service, postgresql and valkey)
docker compose up -d

# View logs
docker compose logs -f 
# View just for specific service
docker compose logs -f hycurrency
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `GRPC_PORT` | `50051` | gRPC server port |
| `DATABASE_URL` | - | PostgreSQL JDBC URL |
| `DATABASE_USER` | - | Database username |
| `DATABASE_PASSWORD` | - | Database password |
| `REDIS_URL` | - | Valkey/Redis URL |
| `CURRENCY_CONFIG_PATH` | `currencies.json` | Path to currency definitions |
| `GRPC_ENABLE_REFLECTION` | `false` | Enable gRPC reflection (dev only) |

### Currency Types (`currencies.json`)

```json
{
  "currencies": [
    {
      "id": "coins",
      "display_name": "Coins",
      "max_balance": -1,
      "negative_behaviour": "strict",
      "max_balance_behaviour": "clamp",
      "transferable": true,
      "starting_balance": 0
    }
  ]
}
```

| Field | Description                                           |
|-------|-------------------------------------------------------|
| `id` | Unique identifier used in API calls                   |
| `display_name` | Human-readable name(might be removed from here)       |
| `max_balance` | Maximum balance a player can hold (`-1` = unlimited)  |
| `starting_balance` | Initial balance for new players                       |
| `transferable` | Whether players can transfer this currency to each other |
| `negative_behaviour` | What happens when deducting more than available       |
| `max_balance_behaviour` | What happens when adding would exceed max_balance     |

**`negative_behaviour`** (on deduct with insufficient funds):
- `strict` - Reject the transaction
- `clamp` - Deduct what's available, return remainder (e.g., have 10, deduct 100 → balance 0, remainder 90)
- `allow` - Allow negative balances

**`max_balance_behaviour`** (on add exceeding max):
- `strict` - Reject the transaction
- `clamp` - Add up to max, return remainder (e.g., max 1000, have 900, add 200 → balance 1000, remainder 100)

## Client Usage

See [client/README.md](client/README.md) for the Kotlin client library.

```kotlin
val client = GrpcCurrencyClient.create("localhost", 50051)

// Get balance
val balance = client.getBalance(playerId, "coins").getOrNull() ?: 0L

// Add currency
client.add("tx-123", playerId, "coins", 100, "reward:quest:example")

// Deduct currency  
client.deduct("tx-456", playerId, "coins", 50, "purchase:shop:example")
```

## Build

```bash
# Service JAR
./gradlew :service:shadowJar
# Output: service/build/libs/hycurrency-service-[current version]-all.jar

# Client JAR
./gradlew :client:shadowJar
# Output: client/build/libs/hycurrency-client-[current version]-all.jar
```

## Project Structure

```
HyCurrency/
├── common/          # Protobuf definitions, shared code
├── client/          # Client library for plugins
├── service/         # gRPC server
├── cache/           # Valkey cache client
└── docker-compose.yml
```
