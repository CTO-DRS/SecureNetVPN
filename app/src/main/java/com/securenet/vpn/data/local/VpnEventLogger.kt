package com.securenet.vpn.data.local

import com.securenet.vpn.data.model.ConnectionState

/**
 * Abstraction over the session log so domain use cases stay testable
 * without Android (EncryptedFile) dependencies.
 */
interface VpnEventLogger {

    fun appendEvent(state: ConnectionState, serverId: String? = null, detail: String? = null)

    fun appendCustom(event: String, detail: String? = null)

    fun appendUsage(dayEpoch: Long, rxBytes: Long, txBytes: Long, connectedSeconds: Long)

    fun clear()
}
