package dall.app.proxycartpro.wifidirect

import android.os.Build

/**
 * Configuration for Wi-Fi Direct server
 */
data class WifiDirectConfig(
    val ssid: String = "ProxyCastPro",
    val password: String = "proxycast123",
    val operatingBand: Int = BAND_2GHZ,
    val operatingChannel: Int = DEFAULT_CHANNEL
) {
    companion object {
        // Operating bands
        const val BAND_2GHZ = 2
        const val BAND_5GHZ = 5
        
        // Default channel
        const val DEFAULT_CHANNEL = 6
        
        /**
         * Check if custom configuration is supported (Android Q+)
         */
        fun canUseCustomConfig(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        }
    }
}
