package com.navrotvpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.navrotvpn.core.ConnectionState
import com.navrotvpn.databinding.ActivityMainBinding
import com.navrotvpn.ui.VpnViewModel
import com.navrotvpn.vpn.AmneziaWgManager
import com.navrotvpn.vpn.WireGuardManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: VpnViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.selectedServer.value?.let { viewModel.connect(it) }
        } else {
            // Пользователь отказал в VPN-разрешении
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WireGuardManager.init(applicationContext)
        AmneziaWgManager.init(applicationContext)

        setupButtons()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.startNetworkMonitoring()
    }

    override fun onStop() {
        viewModel.stopNetworkMonitoring()
        super.onStop()
    }

    // ── Настройка кнопок ────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnConnect.setOnClickListener {
            if (viewModel.connectionState.value == ConnectionState.CONNECTED ||
                viewModel.connectionState.value == ConnectionState.CONNECTING
            ) {
                viewModel.disconnect()
            } else {
                requestPermissionAndConnect()
            }
        }

        binding.btnCopyPublicKey.setOnClickListener {
            viewModel.copyPublicKey(this)
        }

        binding.btnRegenerateKeys.setOnClickListener {
            // Подтверждение перед генерацией новых ключей —
            // после регенерации старый публичный ключ на сервере будет недействителен
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_regen_title)
                .setMessage(R.string.confirm_regen_message)
                .setPositiveButton(R.string.action_regenerate_keys) { _, _ ->
                    viewModel.regenerateKeys(this)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnOpenVpnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        }
    }

    // ── Подписка на ViewModel ───────────────────────────────────────────

    private fun observeViewModel() {
        // Список серверов
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.servers.collect { servers ->
                    if (servers.isEmpty()) return@collect
                    val names = servers.map {
                        "${it.name} (${it.countryCode}) — ${viewModel.protocolLabel(it.protocol)}"
                    }
                    val adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        names
                    )
                    binding.spinnerServer.adapter = adapter
                    binding.spinnerServer.onItemSelectedListener =
                        object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(
                                parent: AdapterView<*>?, view: View?,
                                position: Int, id: Long
                            ) {
                                viewModel.selectServer(servers[position])
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                    // Восстановить выбранную позицию при повороте
                    val current = viewModel.selectedServer.value
                    val idx = servers.indexOf(current)
                    if (idx >= 0) binding.spinnerServer.setSelection(idx)
                    else binding.spinnerServer.setSelection(0)
                }
            }
        }

        // Выбранный сервер → обновляем карточки
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedServer.collect { server ->
                    server ?: return@collect
                    updateProtocolNote(server)
                    updateObfuscationCard(server)
                }
            }
        }

        // Состояние подключения
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.connectionState.collect { state -> updateStatus(state) }
            }
        }

        // Публичный ключ
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.publicKey.collect { key ->
                    binding.tvPublicKey.text = key
                }
            }
        }
    }

    // ── UI-обновления ───────────────────────────────────────────────────

    private fun updateStatus(state: ConnectionState) {
        binding.progressBar.visibility =
            if (state == ConnectionState.CONNECTING) View.VISIBLE else View.GONE
        when (state) {
            ConnectionState.DISCONNECTED -> {
                binding.tvStatus.text = getString(R.string.status_disconnected)
                binding.tvStatus.setTextColor(getColor(R.color.status_disconnected))
                binding.btnConnect.text = getString(R.string.action_connect)
            }
            ConnectionState.CONNECTING -> {
                binding.tvStatus.text = getString(R.string.status_connecting)
                binding.tvStatus.setTextColor(getColor(R.color.status_connecting))
                binding.btnConnect.text = getString(R.string.action_cancel)
            }
            ConnectionState.CONNECTED -> {
                binding.tvStatus.text = getString(R.string.status_connected)
                binding.tvStatus.setTextColor(getColor(R.color.status_connected))
                binding.btnConnect.text = getString(R.string.action_disconnect)
            }
            ConnectionState.ERROR -> {
                binding.tvStatus.text = getString(R.string.status_error)
                binding.tvStatus.setTextColor(getColor(R.color.status_error))
                binding.btnConnect.text = getString(R.string.action_connect)
            }
        }
    }

    private fun updateProtocolNote(server: com.navrotvpn.network.ServerModel) {
        if (viewModel.isProtocolImplemented(server.protocol)) {
            binding.tvProtocolNote.visibility = View.GONE
        } else {
            binding.tvProtocolNote.visibility = View.VISIBLE
            binding.tvProtocolNote.text =
                getString(R.string.protocol_not_implemented, server.protocol.name)
        }
    }

    private fun updateObfuscationCard(server: com.navrotvpn.network.ServerModel) {
        if (server.protocol != com.navrotvpn.network.VpnProtocol.AMNEZIAWG) {
            binding.cardObfuscation.visibility = View.GONE
            return
        }
        binding.cardObfuscation.visibility = View.VISIBLE
        binding.tvObfuscationStatus.text = if (server.jc != null) {
            getString(
                R.string.obfuscation_on,
                server.jc,
                server.jMin ?: 0,
                server.jMax ?: 0
            )
        } else {
            getString(R.string.obfuscation_off)
        }
    }

    // ── Permission flow ─────────────────────────────────────────────────

    private fun requestPermissionAndConnect() {
        val server = viewModel.selectedServer.value ?: return

        // Блокируем попытку подключения к протоколу-заглушке
        if (!viewModel.isProtocolImplemented(server.protocol)) {
            Toast.makeText(
                this,
                getString(R.string.protocol_not_implemented, server.protocol.name),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            viewModel.connect(server)
        }
    }
}