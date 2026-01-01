package dall.app.proxycartpro.proxy

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Simple TCP proxy server for handling HTTP/HTTPS proxy requests
 */
class ProxyServer(
    private val port: Int = 8080,
    private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow<ProxyStatus>(ProxyStatus.Stopped)
    val status = _status.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    /**
     * Start the proxy server
     */
    suspend fun start() {
        if (_status.value is ProxyStatus.Running) {
            Timber.w("Proxy server already running")
            return
        }

        try {
            _status.value = ProxyStatus.Starting

            val selector = SelectorManager(Dispatchers.IO)
            serverSocket = aSocket(selector).tcp().bind(hostname = "0.0.0.0", port = port)

            _status.value = ProxyStatus.Running(port)
            Timber.d("Proxy server started on port $port")

            serverJob = scope.launch {
                try {
                    while (isActive) {
                        val clientSocket = serverSocket?.accept()
                        if (clientSocket != null) {
                            launch {
                                handleClient(clientSocket)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Timber.e(e, "Error in proxy server loop")
                        _status.value = ProxyStatus.Error(e.message ?: "Unknown error")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start proxy server")
            _status.value = ProxyStatus.Error(e.message ?: "Unknown error")
            throw e
        }
    }

    /**
     * Stop the proxy server
     */
    suspend fun stop() {
        try {
            _status.value = ProxyStatus.Stopping

            serverJob?.cancel()
            serverJob = null

            serverSocket?.close()
            serverSocket = null

            _status.value = ProxyStatus.Stopped
            Timber.d("Proxy server stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping proxy server")
            _status.value = ProxyStatus.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Handle a client connection
     */
    private suspend fun handleClient(clientSocket: Socket) {
        try {
            val receiveChannel = clientSocket.openReadChannel()
            val sendChannel = clientSocket.openWriteChannel(autoFlush = true)

            // Read the initial request
            val requestLine = receiveChannel.readUTF8Line()
            Timber.d("Received request: $requestLine")

            if (requestLine == null) {
                clientSocket.close()
                return
            }

            // Parse request (simplified)
            val parts = requestLine.split(" ")
            if (parts.size < 3) {
                sendErrorResponse(sendChannel, 400, "Bad Request")
                clientSocket.close()
                return
            }

            val method = parts[0]
            val url = parts[1]

            // For CONNECT method (HTTPS proxy)
            if (method == "CONNECT") {
                handleConnectMethod(clientSocket, sendChannel, url)
            } else {
                // For regular HTTP proxy
                handleHttpMethod(clientSocket, receiveChannel, sendChannel, requestLine)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling client")
        } finally {
            try {
                clientSocket.close()
            } catch (e: Exception) {
                Timber.e(e, "Error closing client socket")
            }
        }
    }

    /**
     * Handle HTTP CONNECT method for HTTPS tunneling
     */
    private suspend fun handleConnectMethod(
        clientSocket: Socket,
        sendChannel: ByteWriteChannel,
        hostPort: String
    ) {
        try {
            // Parse host and port
            val parts = hostPort.split(":")
            if (parts.size != 2) {
                sendErrorResponse(sendChannel, 400, "Bad Request")
                return
            }

            val host = parts[0]
            val port = parts[1].toIntOrNull() ?: 443

            // Connect to remote server
            val selector = SelectorManager(Dispatchers.IO)
            val remoteSocket = aSocket(selector).tcp().connect(hostname = host, port = port)

            // Send success response
            sendChannel.writeStringUtf8("HTTP/1.1 200 Connection Established\r\n\r\n")
            sendChannel.flush()

            // Start bidirectional forwarding
            val job1 = scope.launch {
                try {
                    val clientReceive = clientSocket.openReadChannel()
                    val remoteSend = remoteSocket.openWriteChannel(autoFlush = true)
                    clientReceive.copyTo(remoteSend)
                } catch (e: Exception) {
                    Timber.d("Client to remote forwarding ended: ${e.message}")
                }
            }

            val job2 = scope.launch {
                try {
                    val remoteReceive = remoteSocket.openReadChannel()
                    val clientSend = clientSocket.openWriteChannel(autoFlush = true)
                    remoteReceive.copyTo(clientSend)
                } catch (e: Exception) {
                    Timber.d("Remote to client forwarding ended: ${e.message}")
                }
            }

            // Wait for both to complete
            joinAll(job1, job2)
            remoteSocket.close()
        } catch (e: Exception) {
            Timber.e(e, "Error in CONNECT method")
            sendErrorResponse(sendChannel, 502, "Bad Gateway")
        }
    }

    /**
     * Handle regular HTTP method
     */
    private suspend fun handleHttpMethod(
        clientSocket: Socket,
        receiveChannel: ByteReadChannel,
        sendChannel: ByteWriteChannel,
        requestLine: String
    ) {
        try {
            // Read all headers
            val headers = mutableListOf<String>()
            while (true) {
                val line = receiveChannel.readUTF8Line()
                if (line.isNullOrEmpty()) break
                headers.add(line)
            }

            // Parse URL to get host
            val parts = requestLine.split(" ")
            val url = parts[1]
            
            // For simplicity, return a simple response
            sendErrorResponse(sendChannel, 501, "Not Implemented")
        } catch (e: Exception) {
            Timber.e(e, "Error in HTTP method")
            sendErrorResponse(sendChannel, 500, "Internal Server Error")
        }
    }

    /**
     * Send an error response
     */
    private suspend fun sendErrorResponse(channel: ByteWriteChannel, code: Int, message: String) {
        try {
            channel.writeStringUtf8("HTTP/1.1 $code $message\r\n")
            channel.writeStringUtf8("Content-Length: 0\r\n")
            channel.writeStringUtf8("Connection: close\r\n")
            channel.writeStringUtf8("\r\n")
            channel.flush()
        } catch (e: Exception) {
            Timber.e(e, "Error sending error response")
        }
    }

    /**
     * Proxy status sealed class
     */
    sealed class ProxyStatus {
        object Stopped : ProxyStatus()
        object Starting : ProxyStatus()
        data class Running(val port: Int) : ProxyStatus()
        object Stopping : ProxyStatus()
        data class Error(val message: String) : ProxyStatus()
    }
}
