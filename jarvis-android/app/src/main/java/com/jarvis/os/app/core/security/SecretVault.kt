package com.jarvis.os.app.core.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 10 "Secret protection": in-process, in-memory-only secret
 * storage (e.g. a future real ChatProvider's API key) with no plain
 * `toString`/logging exposure -- `masked()` is the only way to observe
 * a stored value without exact-key retrieval via `reveal()`.
 *
 * Honesty note: this is NOT Android EncryptedSharedPreferences/Keystore
 * -- values live in a plain in-memory map and are lost on process
 * death, same limitation every other Mock* repository here has (see
 * ConnectionRepository's module docstring on why persistence is
 * consistently deferred). Wiring real encrypted, disk-persisted
 * storage is one class implementing this same interface -- the swap
 * point every other Mock/real boundary in this codebase already uses.
 * What IS real here: no secret value ever appears in this class's own
 * logs, exceptions, or equals/toString surface -- `store` returns
 * Unit, `reveal` requires the exact key, and `masked` never returns
 * more than the last 4 characters.
 */
interface SecretVault {
    fun store(key: String, value: String)
    fun reveal(key: String): String?
    fun masked(key: String): String?
    fun remove(key: String)
    fun keys(): Set<String>
}

@Singleton
class InMemorySecretVault @Inject constructor() : SecretVault {
    private val secrets = mutableMapOf<String, String>()

    override fun store(key: String, value: String) {
        secrets[key] = value
    }

    override fun reveal(key: String): String? = secrets[key]

    override fun masked(key: String): String? {
        val value = secrets[key] ?: return null
        val visible = value.takeLast(4)
        return "*".repeat((value.length - visible.length).coerceAtLeast(0)) + visible
    }

    override fun remove(key: String) {
        secrets.remove(key)
    }

    override fun keys(): Set<String> = secrets.keys.toSet()
}
