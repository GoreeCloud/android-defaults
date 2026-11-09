package com.goreecloud.dialer

import java.util.IdentityHashMap
import java.util.UUID

/**
 * A notification can only act on its originating live Telecom Call.
 * Identity semantics matter: two call objects that compare equal must not
 * share a notification action token.
 *
 * Only store these tokens in memory; never persist, log or export them.
 */
internal class LiveCallTokens<T : Any> {
    private val byCall = IdentityHashMap<T, String>()

    fun register(call: T): String {
        val existing = byCall[call]
        if (existing != null) return existing
        val token = UUID.randomUUID().toString()
        byCall[call] = token
        return token
    }

    fun token(call: T): String? = byCall[call]

    fun resolve(token: String?): T? {
        if (token.isNullOrEmpty()) return null
        return byCall.entries.firstOrNull { it.value == token }?.key
    }

    fun remove(call: T): Boolean = byCall.remove(call) != null

    fun clear() { byCall.clear() }
}
