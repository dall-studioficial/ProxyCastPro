package dall.app.proxycartpro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dall.app.proxycartpro.ui.theme.ProxyCartProTheme
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private lateinit var tetheringManager: TetheringManager

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Timber.d("All permissions granted")
        } else {
            Timber.w("Some permissions were not granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize tethering manager
        tetheringManager = TetheringManager(applicationContext)

        // Request permissions
        requestRequiredPermissions()

        enableEdgeToEdge()
        setContent {
            ProxyCartProTheme {
                TetheringScreen(tetheringManager)
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Wi-Fi Direct requires location permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Android 13+ requires NEARBY_WIFI_DEVICES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TetheringScreen(tetheringManager: TetheringManager) {
    val tetheringState by tetheringManager.tetheringState.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("ProxyCast Pro") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Wi-Fi Direct Tethering Status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    when (val state = tetheringState) {
                        is TetheringManager.TetheringState.Stopped -> {
                            Text("Status: Stopped", style = MaterialTheme.typography.bodyMedium)
                        }
                        is TetheringManager.TetheringState.Starting -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Starting...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is TetheringManager.TetheringState.Running -> {
                            Text("Status: Running", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("SSID: ${state.ssid}", style = MaterialTheme.typography.bodySmall)
                            Text("Password: ${state.password}", style = MaterialTheme.typography.bodySmall)
                        }
                        is TetheringManager.TetheringState.Stopping -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stopping...", style = MaterialTheme.typography.bodyMedium)
                        }
                        is TetheringManager.TetheringState.Error -> {
                            Text(
                                "Error: ${state.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Control Buttons
            val isRunning = tetheringState is TetheringManager.TetheringState.Running
            val isTransitioning = tetheringState is TetheringManager.TetheringState.Starting ||
                    tetheringState is TetheringManager.TetheringState.Stopping

            Button(
                onClick = {
                    scope.launch {
                        if (isRunning) {
                            tetheringManager.stopTethering()
                        } else {
                            tetheringManager.startTethering()
                        }
                    }
                },
                enabled = !isTransitioning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "Stop Tethering" else "Start Tethering")
            }

            // Information Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "How to Connect",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Start tethering on this device\n" +
                                "2. On your other device, go to Wi-Fi settings\n" +
                                "3. Connect to the Wi-Fi network shown above\n" +
                                "4. Configure proxy settings to point to this device's IP address on port 8080",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
