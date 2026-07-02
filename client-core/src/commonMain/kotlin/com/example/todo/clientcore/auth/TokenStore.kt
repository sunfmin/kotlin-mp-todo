package com.example.todo.clientcore.auth

/** The session a signed-in client holds. */
data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
    val email: String,
)

/**
 * Where a client keeps its tokens. Platform apps supply a secure/persistent
 * implementation (Keychain / Keystore / localStorage); tests use the in-memory one.
 */
interface TokenStore {
    fun load(): StoredTokens?
    fun save(tokens: StoredTokens)
    fun clear()
}

class InMemoryTokenStore(initial: StoredTokens? = null) : TokenStore {
    private var tokens: StoredTokens? = initial
    override fun load(): StoredTokens? = tokens
    override fun save(tokens: StoredTokens) { this.tokens = tokens }
    override fun clear() { tokens = null }
}
