package com.navrotvpn.vpn

import android.util.Log
import org.amnezia.awg.backend.GoBackend

/**
 * Аналог VpnServiceImpl для AmneziaWG.
 */
class AmneziaVpnServiceImpl : GoBackend.VpnService() {

    companion object {
        private const val TAG = "AmneziaVpnService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AmneziaVpnServiceImpl создан")
    }

    override fun onDestroy() {
        Log.d(TAG, "AmneziaVpnServiceImpl уничтожен")
        super.onDestroy()
    }
}