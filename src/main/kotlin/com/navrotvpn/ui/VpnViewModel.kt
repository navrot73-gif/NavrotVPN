package com.navrotvpn.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navrotvpn.R
import com.navrotvpn.core.ConfigManager
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.core.KeyManager
import com.navrotvpn.network.ApiClient
import com.navrotvpn.network.ServerModel
import com.navrotvpn.network.VpnProtocol
import com.navrotvpn.vpn.NetworkMonitor
import com.navrotvpn.vpn.protocol.ProtocolRouter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel выносит всю бизнес-логику из Activity.
 * Преимущества:
 * - Состояние переживает поворот экрана (configuration change)
 * - Activity не хранит mutable state
 * - Легко тестировать без UI
 */
class VpnViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VpnViewModel"
    }

    // ── Состояние UI ───────────────────────────────────────────────────

    val servers: StateFlow<List<ServerModel>> = MutableStateFlow(emptyList())

    private val _selectedServer = MutableStateFlow<ServerModel?>(null)
    val selectedServer: StateFlow<ServerModel?> = _selectedServer.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _publicKey = MutableStateFlow("")
    val publicKey: StateFlow<String> = _publicKey.asStateFlow()

    // ── Внутреннее состояние ───────────────────────────────────────────

    private val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(application, viewModelScope)
    }

    private var stateCollectJob: Job? = null

    // ── Инициализация ──────────────────────────────────────────────────

    init {
        showPublicKey()
        loadServers()
    }

    fun startNetworkMonitoring() {
        networkMonitor.start()
    }

    fun stopNetworkMonitoring() {
        networkMonitor.stop()
    }

    // ── Публичные методы для Activity ──────────────────────────────────

    fun selectServer(server: ServerModel) {
        _selectedServer.value = server
        observeProtocolState(server.protocol)
    }

    fun toggleConnection() {
        if (ProtocolRouter.isConnected()) {
            disconnect()
        } else {
            // Ничего не делаем здесь — permission flow идёт через Activity
        }
    }

    fun connect(server: ServerModel) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            try {
                ProtocolRouter.connect(getApplication(), server)
            } catch (e: NotImplementedError) {
                _connectionState.value = ConnectionState.ERROR
                Log.w(TAG, "Протокол не реализован: ${e.message}")
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                Log.e(TAG, "Ошибка подключения", e)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch { ProtocolRouter.disconnect() }
    }

    fun copyPublicKey(context: Context) {
        val key = _publicKey.value
        if (key.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WireGuard public key", key))
        Toast.makeText(context, R.string.public_key_copied, Toast.LENGTH_SHORT).show()
    }

    fun regenerateKeys(context: Context) {
        KeyManager.regenerateKeyPair(context)
        showPublicKey()
        Toast.makeText(context, R.string.keys_regenerated, Toast.LENGTH_LONG).show()
    }

    fun isProtocolImplemented(protocol: VpnProtocol): Boolean =
        ProtocolRouter.isImplemented(protocol)

    fun protocolLabel(protocol: VpnProtocol): String =
        if (ProtocolRouter.isImplemented(protocol)) protocol.name
        else "${protocol.name} \u26A0"

    // ── Приватные методы ───────────────────────────────────────────────

    private fun showPublicKey() {
        _publicKey.value = KeyManager.publicKeyBase64(getApplication())
    }

    private fun loadServers() {
        viewModelScope.launch {
            var list = ApiClient.fetchServerList()
            if (list.isEmpty()) {
                list = ConfigManager.loadBundledServers(getApplication())
            }
            (servers as MutableStateFlow).value = list
            _selectedServer.value = list.firstOrNull()
            if (list.isNotEmpty()) {
                observeProtocolState(list.first().protocol)
            }
        }
    }

    private fun observeProtocolState(protocol: VpnProtocol) {
        stateCollectJob?.cancel()
        stateCollectJob = viewModelScope.launch {
            ProtocolRouter.stateFor(protocol).collect { state ->
                _connectionState.value = state
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stateCollectJob?.cancel()
        networkMonitor.stop()
    }
}