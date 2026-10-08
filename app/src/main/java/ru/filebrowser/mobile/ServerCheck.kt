package ru.filebrowser.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket

object ServerCheck {

    enum class Error {
        INVALID_URL,
        DNS_ERROR,
        CONNECTION_REFUSED,
        TIMEOUT,
        SSL_UNRECOGNIZED_NAME,
        SSL_CERTIFICATE,
        SSL_OTHER,
        BACKEND_DOWN,
        SERVICE_UNAVAILABLE,
        GATEWAY_TIMEOUT,
        SERVER_ERROR,
        UNKNOWN
    }

    suspend fun check(url: String): Error? = withContext(Dispatchers.IO) {
        try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host ?: return@withContext Error.INVALID_URL
            val port = when {
                uri.port != -1 -> uri.port
                scheme == "https" -> 443
                else -> 80
            }

            if (scheme == "https") {
                val tlsError = checkTls(host, port)
                if (tlsError != null) return@withContext tlsError
            }

            val httpError = checkHttp(url)
            if (httpError != null) return@withContext httpError

            null
        } catch (e: UnknownHostException) {
            Error.DNS_ERROR
        } catch (e: Exception) {
            Error.UNKNOWN
        }
    }

    private fun checkTls(host: String, port: Int): Error? {
        var socket: SSLSocket? = null
        return try {
            val factory = SSLContext.getDefault().socketFactory
            socket = factory.createSocket() as SSLSocket
            socket.connect(InetSocketAddress(host, port), 7000)

            val params = socket.sslParameters
            params.serverNames = listOf(SNIHostName(host))
            socket.sslParameters = params
            socket.soTimeout = 7000

            socket.startHandshake()
            null
        } catch (e: SSLHandshakeException) {
            val msg = e.message ?: ""
            when {
                msg.contains("unrecognized_name", ignoreCase = true) -> Error.SSL_UNRECOGNIZED_NAME
                msg.contains("certificate", ignoreCase = true) ||
                        msg.contains("PKIX", ignoreCase = true) -> Error.SSL_CERTIFICATE
                else -> Error.SSL_OTHER
            }
        } catch (e: java.net.SocketTimeoutException) {
            Error.TIMEOUT
        } catch (e: java.net.ConnectException) {
            Error.CONNECTION_REFUSED
        } catch (e: Exception) {
            Error.SSL_OTHER
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun checkHttp(url: String): Error? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FileBrowserMobile/1.0")
                setRequestProperty("Accept", "text/html,*/*")
            }
            val code = conn.responseCode
            when {
                code in 200..399 -> null
                code == 401 || code == 403 -> null
                code == 502 -> Error.BACKEND_DOWN
                code == 503 -> Error.SERVICE_UNAVAILABLE
                code == 504 -> Error.GATEWAY_TIMEOUT
                code in 500..599 -> Error.SERVER_ERROR
                else -> Error.UNKNOWN
            }
        } catch (e: Exception) {
            Error.UNKNOWN
        } finally {
            conn?.disconnect()
        }
    }
}