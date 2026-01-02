package dall.app.proxycartpro.wifidirect

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.os.Build
import android.os.Looper
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Wi-Fi Direct server implementation for creating and managing Wi-Fi Direct groups
 */
class WifiDirectServer(
    private val appContext: Context,
    private val scope: CoroutineScope
) {

    private val wifiP2PManager by lazy {
        appContext.getSystemService<WifiP2pManager>()
            ?: throw IllegalStateException("WifiP2pManager not available")
    }

    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.Idle)
    val networkStatus: Flow<NetworkStatus> = _networkStatus.asStateFlow()

    private var currentChannel: Channel? = null

    @SuppressLint("MissingPermission")
    private fun createGroupQ(
        channel: Channel,
        config: WifiP2pConfig,
        listener: WifiP2pManager.ActionListener,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiP2PManager.createGroup(
                channel,
                config,
                listener,
            )
        } else {
            throw IllegalStateException("Called createGroupQ but not Q: ${Build.VERSION.SDK_INT}")
        }
    }

    private suspend fun removeGroup(channel: Channel) {
        Timber.d("Stop existing WiFi Group")
        return suspendCoroutine { cont ->
            wifiP2PManager.removeGroup(
                channel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Timber.d("Wifi P2P Channel is removed")
                        cont.resume(Unit)
                    }

                    override fun onFailure(reason: Int) {
                        Timber.e("Error removing WiFi P2P Channel: $reason")
                        cont.resumeWithException(
                            RuntimeException("Unable to remove Wifi P2P Channel: $reason")
                        )
                    }
                },
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun createGroup(
        channel: Channel,
        config: WifiP2pConfig,
    ) {
        Timber.d("Create WiFi Group")
        return suspendCoroutine { cont ->
            val listener = object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Timber.d("Wifi P2P Channel is created")
                    cont.resume(Unit)
                }

                override fun onFailure(reason: Int) {
                    Timber.e("Error creating WiFi P2P Channel: $reason")
                    cont.resumeWithException(
                        RuntimeException("Unable to create Wifi P2P Channel: $reason")
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createGroupQ(channel, config, listener)
            } else {
                wifiP2PManager.createGroup(channel, listener)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestGroupInfo(channel: Channel): WifiP2pGroup? {
        return suspendCoroutine { cont ->
            wifiP2PManager.requestGroupInfo(channel) { group ->
                cont.resume(group)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestConnectionInfo(channel: Channel): WifiP2pInfo? {
        return suspendCoroutine { cont ->
            wifiP2PManager.requestConnectionInfo(channel) { info ->
                cont.resume(info)
            }
        }
    }

    /**
     * Start the Wi-Fi Direct server
     */
    suspend fun start(ssid: String = "ProxyCastPro", password: String = "proxycast123") {
        try {
            _networkStatus.value = NetworkStatus.Starting

            // Initialize channel
            val channel = wifiP2PManager.initialize(appContext, Looper.getMainLooper(), null)
                ?: throw RuntimeException("Unable to create Wi-Fi Direct Channel")

            currentChannel = channel

            // Remove existing group if any
            try {
                removeGroup(channel)
            } catch (e: Exception) {
                Timber.w(e, "No existing group to remove")
            }

            // Create configuration
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiP2pConfig.Builder()
                    .setNetworkName(ssid)
                    .setPassphrase(password)
                    .build()
            } else {
                WifiP2pConfig()
            }

            // Create group
            createGroup(channel, config)

            // Get group info
            val groupInfo = requestGroupInfo(channel)
            val connectionInfo = requestConnectionInfo(channel)

            if (groupInfo != null && connectionInfo != null) {
                _networkStatus.value = NetworkStatus.Running(
                    ssid = groupInfo.networkName ?: ssid,
                    password = groupInfo.passphrase ?: password,
                    ipAddress = connectionInfo.groupOwnerAddress?.hostAddress ?: "Unknown"
                )
                Timber.d("Wi-Fi Direct group started: ${groupInfo.networkName}")
            } else {
                throw RuntimeException("Failed to get group information")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start Wi-Fi Direct server")
            _networkStatus.value = NetworkStatus.Error(e.message ?: "Unknown error")
            throw e
        }
    }

    /**
     * Stop the Wi-Fi Direct server
     */
    suspend fun stop() {
        try {
            _networkStatus.value = NetworkStatus.Stopping

            currentChannel?.let { channel ->
                removeGroup(channel)
            }

            _networkStatus.value = NetworkStatus.Idle
            Timber.d("Wi-Fi Direct server stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping Wi-Fi Direct server")
            _networkStatus.value = NetworkStatus.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Network status sealed class
     */
    sealed class NetworkStatus {
        object Idle : NetworkStatus()
        object Starting : NetworkStatus()
        data class Running(
            val ssid: String,
            val password: String,
            val ipAddress: String
        ) : NetworkStatus()
        object Stopping : NetworkStatus()
        data class Error(val message: String) : NetworkStatus()
    }
}
