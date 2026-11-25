package com.audioassistant.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Journal only grants acquired by this implementation; never revoke untracked legacy grants. */
class UriGrantLedger(context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("owned_uri_grants", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private fun owned() = preferences.getStringSet("uris", emptySet()).orEmpty().toMutableSet()
    private fun write(values: Set<String>) { check(preferences.edit().putStringSet("uris", values).commit()) { "无法记录文件授权" } }
    private fun mark(uri: Uri) { write(owned().apply { add(uri.toString()) }) }

    suspend fun takeRead(uri: Uri): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) return@withLock true
        mark(uri) // before acquisition: process death cannot create an untracked new grant
        try { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: Exception) { return@withLock false }
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    } }

    suspend fun releaseUnused(protectedUris: Set<String>) = withContext(Dispatchers.IO) { mutex.withLock {
        val tracked = owned()
        val original = tracked.toSet()
        val needed = protectedUris.map(Uri::parse)
        tracked.toList().forEach { value ->
            val uri = Uri.parse(value)
            val tree = runCatching { DocumentsContract.isTreeUri(uri) }.getOrDefault(false)
            // A disconnected provider cannot prove ancestry; conservatively retain its tree grant.
            if (uri in needed || tree && needed.any { it.authority == uri.authority }) return@forEach
            val grant = resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
            if (grant == null) tracked.remove(value)
            else try {
                val flags = (if (grant.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                    (if (grant.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                resolver.releasePersistableUriPermission(uri, flags); tracked.remove(value)
            } catch (_: SecurityException) { /* Retry later, without affecting source files. */ }
        }
        if (tracked != original) write(tracked)
    } }
}
