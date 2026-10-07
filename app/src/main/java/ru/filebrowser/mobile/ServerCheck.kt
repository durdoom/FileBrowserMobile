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

    suspend fun check(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host ?: return@withContext "Некорректный адрес."
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
            "Не удалось найти сервер (DNS). Проверьте адрес."
        } catch (e: Exception) {
            "Не удалось подключиться: ${e.message ?: "неизвестная ошибка"}"
        }
    }

    private fun checkTls(host: String, port: Int): String? {
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
                msg.contains("unrecognized_name", ignoreCase = true) ->
                    "Сервер не знает домен $host. Проверьте SSL/домен в NPM."
                msg.contains("certificate", ignoreCase = true) ||
                        msg.contains("PKIX", ignoreCase = true) ->
                    "Проблема с сертификатом сервера. Проверьте SSL в NPM."
                else ->
                    "Ошибка защищённого соединения: $msg"
            }
        } catch (e: java.net.SocketTimeoutException) {
            "Сервер не отвечает (таймаут)."
        } catch (e: java.net.ConnectException) {
            "Сервер не отвечает. Возможно, он выключен или недоступен."
        } catch (e: Exception) {
            "Ошибка SSL: ${e.message ?: "неизвестная"}"
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun checkHttp(url: String): String? {
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
                code == 502 -> "Бэкенд FileBrowser не отвечает (502). Проверьте контейнер."
                code == 503 -> "Сервис временно недоступен (503)."
                code == 504 -> "Сервер не получил ответ от бэкенда (504)."
                code in 500..599 -> "Сервер вернул ошибку $code."
                else -> "Сервер ответил кодом $code."
            }
        } catch (e: Exception) {
            "Ошибка HTTP: ${e.message ?: "неизвестная"}"
        } finally {
            conn?.disconnect()
        }
    }
}