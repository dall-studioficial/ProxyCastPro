package dall.app.proxycartpro.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import timber.log.Timber

/**
 * Broadcast receiver for Wi-Fi Direct state changes
 */
class WifiDirectReceiver(
    private val onStateChanged: (Boolean) -> Unit,
    private val onPeersChanged: () -> Unit,
    private val onConnectionChanged: () -> Unit,
    private val onThisDeviceChanged: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                Timber.d("WiFi Direct: ${if (isEnabled) "Enabled" else "Disabled"}")
                onStateChanged(isEnabled)
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Timber.d("WiFi Direct: Peers changed")
                onPeersChanged()
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                Timber.d("WiFi Direct: Connection changed")
                onConnectionChanged()
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                Timber.d("WiFi Direct: This device changed")
                onThisDeviceChanged()
            }
        }
    }

    companion object {
        /**
         * Get the intent filter for Wi-Fi Direct broadcasts
         */
        fun getIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
        }
    }
}
