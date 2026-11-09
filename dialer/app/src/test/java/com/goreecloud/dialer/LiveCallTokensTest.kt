package com.goreecloud.dialer

import org.junit.Assert.*
import org.junit.Test

class LiveCallTokensTest {
    private data class EqualCall(val id: Int)

    @Test fun equalButDistinctCallsCannotShareTokens() {
        val calls = LiveCallTokens<EqualCall>()
        val a = EqualCall(42)
        val b = EqualCall(42)
        assertEquals(a, b)
        assertNotSame(a, b)
        val tokenA = calls.register(a)
        val tokenB = calls.register(b)
        assertNotEquals(tokenA, tokenB)
        assertSame(a, calls.resolve(tokenA))
        assertSame(b, calls.resolve(tokenB))
    }

    @Test fun duplicateRegistrationKeepsStableTokenAndRemovedCallCannotBeControlled() {
        val calls = LiveCallTokens<Any>()
        val original = Any()
        val survivor = Any()
        val firstToken = calls.register(original)
        assertEquals(firstToken, calls.register(original))
        val secondToken = calls.register(survivor)
        assertTrue(calls.remove(original))
        assertNull(calls.resolve(firstToken))
        assertFalse(calls.remove(original))
        assertSame(survivor, calls.resolve(secondToken))
        calls.clear()
        assertNull(calls.resolve(secondToken))
        assertNull(calls.resolve(null))
        assertNull(calls.resolve(""))
    }
}
