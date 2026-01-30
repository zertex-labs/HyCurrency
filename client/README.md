# HyCurrency Client

## Usage
NOTE: for transactionId check "Idempotency"
```kotlin
val client = GrpcCurrencyClient.create("localhost", 50051)

val player = PlayerId.fromString("550e8400-e29b-41d4-a716-446655440000")

val balance = client.getBalance(player, "coins").getOrNull() ?: 0L

client.add(
    transactionId = "quest:example:${player.uuid}",
    playerId = player,
    currencyType = "coins",
    amount = 100,
    reason = "quest_reward"
).onSuccess { result ->
    println("New balance: ${result.newBalance}")
}

client.deduct(
    transactionId = "shop:${player.uuid}:diamond_sword:${System.currentTimeMillis()}",
    playerId = player,
    currencyType = "coins",
    amount = 50,
    reason = "shop_purchase"
)

client.transfer(
    transactionId = "trade:${player.uuid}:${otherPlayer.uuid}:${System.currentTimeMillis()}",
    fromPlayerId = player,
    toPlayerId = otherPlayer,
    currencyType = "coins",
    amount = 25
)

// close when done
client.close()
```

## Result Handling
All operations return `CurrencyResult<T>`:

```kotlin
when (val result = client.deduct(...)) {
    is CurrencyResult.Success -> println("New balance: ${result.data.newBalance}")
    is CurrencyResult.InsufficientFunds -> println("Need ${result.required}, have ${result.available}")
    is CurrencyResult.PlayerNotFound -> println("Player doesn't exist")
    is CurrencyResult.Error -> println("Error: ${result.message}")
}
```

Extension functions:
```kotlin
result.getOrNull()      // T? - null on failure
result.getOrThrow()     // T  - throws on failure
result.getOrElse { 0 }  // T  - default on failure
result.onSuccess { }    // callback on success
result.map { }          // transform success value
```

## Exceptions
`InvalidCurrencyTypeException` is thrown(not returned) when using an invalid currency type. This is a programming error - verify your currency types matches the client config(currencies.json).

## Idempotency
The `transactionId` ensures operations aren't duplicated on retry. Use deterministic IDs:
(think someone sending 5 packets at the same time; only one will be processed, the others will return the result of the first one and won't do any actual work)

```kotlin
// Good - same ID for same logical operation
val txId = "shop:${player.uuid}:${itemId}:${timestamp}"

// "Bad" - random ID will still work, but won't protect from anything
val txId = UUID.randomUUID().toString()
```
