package dall.app.proxycartpro

import android.content.Context
import dall.app.proxycartpro.proxy.ProxyServer
import dall.app.proxycartpro.wifidirect.WifiDirectConfig
import dall.app.proxycartpro.wifidirect.WifiDirectServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manager for Wi-Fi Direct tethering and proxy server
 */
class TetheringManager(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val wifiDirectServer = WifiDirectServer(context, scope)
    private val proxyServer = ProxyServer(port = 8080, scope = scope)
    
    private val _tetheringState = MutableStateFlow<TetheringState>(TetheringState.Stopped)
    val tetheringState = _tetheringState.asStateFlow()
    
    /**
     * Start tethering (Wi-Fi Direct + Proxy Server)
     */
    fun startTethering(
        ssid: String = "ProxyCastPro",
        password: String = "proxycast123"
    ) {
        scope.launch {
            try {
                _tetheringState.value = TetheringState.Starting
                Timber.d("Starting tethering...")
                
                // Start Wi-Fi Direct
                wifiDirectServer.start(ssid, password)
                
                // Start Proxy Server
                proxyServer.start()
                
                _tetheringState.value = TetheringState.Running(ssid, password)
                Timber.d("Tethering started successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start tethering")
                _tetheringState.value = TetheringState.Error(e.message ?: "Unknown error")
                
                // Try to clean up
                try {
                    stopTethering()
                } catch (cleanupError: Exception) {
                    Timber.e(cleanupError, "Error during cleanup")
                }
            }
        }
    }
    
    /**
     * Stop tethering
     */
    fun stopTethering() {
        scope.launch {
            try {
                _tetheringState.value = TetheringState.Stopping
                Timber.d("Stopping tethering...")
                
                // Stop proxy server
                proxyServer.stop()
                
                // Stop Wi-Fi Direct
                wifiDirectServer.stop()
                
                _tetheringState.value = TetheringState.Stopped
                Timber.d("Tethering stopped successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error stopping tethering")
                _tetheringState.value = TetheringState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Tethering state sealed class
     */
    sealed class TetheringState {
        object Stopped : TetheringState()
        object Starting : TetheringState()
        data class Running(val ssid: String, val password: String) : TetheringState()
        object Stopping : TetheringState()
        data class Error(val message: String) : TetheringState()
    }
}
