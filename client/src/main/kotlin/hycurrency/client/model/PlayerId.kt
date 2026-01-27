package hycurrency.client.model

import java.util.UUID

@JvmInline
value class PlayerId(val uuid: UUID) {
    override fun toString(): String = uuid.toString()

    companion object {
        fun fromString(value: String) = PlayerId(UUID.fromString(value))
        fun random() = PlayerId(UUID.randomUUID())
    }
}