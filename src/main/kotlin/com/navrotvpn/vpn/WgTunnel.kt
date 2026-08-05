package com.navrotvpn.vpn

import com.wireguard.android.backend.Tunnel

/**
 * Минимальная реализация Tunnel, которую требует Backend.setState().
 * onStateChange — прокидывает колбэк наверх (в WireGuardManager),
 * чтобы обновить UI.
 */
class WgTunnel(
    private val tunnelName: String,
    private val onStateChanged: (Tunnel.State) -> Unit
) : Tunnel {

    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        onStateChanged(newState)
    }
}