package com.goreecloud.dialer

/**
 * Generation guard for Android provider callbacks.
 * Update from the main thread; background worker results are posted to main
 * before checking isCurrent(), preventing recycled Activity state publication.
 */
internal class RequestGeneration {
    private var version = 0L
    fun next(): Long = ++version
    fun invalidate() { version++ }
    fun isCurrent(request: Long): Boolean = request == version
}
