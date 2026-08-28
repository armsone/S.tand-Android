package com.armsone.stand.platform

import com.armsone.stand.model.InternetRadioCodec
import com.armsone.stand.model.InternetRadioConfiguration
import com.armsone.stand.model.RadioDecodeResult
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal enum class RadioTransferPayloadDecision { ACCEPT, LENGTH_REQUIRED, TOO_LARGE }

internal object RadioTransferRequestPolicy {
    fun tokenMatches(expected: String, provided: String?): Boolean =
        expected.isNotBlank() && expected == provided

    fun payloadDecision(contentLength: Int): RadioTransferPayloadDecision = when {
        contentLength <= 0 -> RadioTransferPayloadDecision.LENGTH_REQUIRED
        contentLength > InternetRadioCodec.MAX_PAYLOAD_BYTES -> RadioTransferPayloadDecision.TOO_LARGE
        else -> RadioTransferPayloadDecision.ACCEPT
    }
}

class RadioTransferServer(
    private val onStarted: (uploadUrl: String, fallbackAddress: String, token: String) -> Unit,
    private val onSuccess: (channels: List<InternetRadioConfiguration>) -> Unit,
    private val onError: (message: String) -> Unit,
    private val onClosed: () -> Unit = {},
) : Closeable {

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val secureToken: String = generateToken()

    val token: String get() = secureToken

    fun start(): Boolean {
        if (isRunning.getAndSet(true)) return true

        val localIp = findLocalIpAddress()
        if (localIp == null) {
            isRunning.set(false)
            onError("동일한 Wi-Fi 네트워크 연결을 찾을 수 없습니다. USB 또는 파일에서 가져오기를 사용해 주세요.")
            return false
        }

        try {
            val server = ServerSocket(0)
            serverSocket = server
            val port = server.localPort
            val uploadUrl = "http://$localIp:$port/?token=$secureToken"
            val fallbackAddress = "http://$localIp:$port"

            onStarted(uploadUrl, fallbackAddress, secureToken)

            // Auto-timeout after 3 minutes
            scheduler.schedule({
                if (isRunning.get()) {
                    close()
                }
            }, SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            executor.execute {
                listenLoop(server)
            }
            return true
        } catch (e: Exception) {
            isRunning.set(false)
            onError("로컬 수신 서버를 시작할 수 없습니다: ${e.message}")
            return false
        }
    }

    private fun listenLoop(server: ServerSocket) {
        while (isRunning.get()) {
            try {
                val client = server.accept()
                client.soTimeout = SOCKET_TIMEOUT_MILLIS
                executor.execute {
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    // Socket closed or error
                }
                break
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                val input = BufferedInputStream(client.getInputStream())
                val output = BufferedOutputStream(client.getOutputStream())

                val requestHeader = readHeader(input) ?: return
                val firstLine = requestHeader.lines().firstOrNull() ?: return
                val parts = firstLine.split(" ")
                if (parts.size < 2) {
                    sendResponse(output, 400, "Bad Request", "text/plain; charset=utf-8", "잘못된 요청입니다.")
                    return
                }

                val method = parts[0].uppercase()
                val rawUri = parts[1]
                val uri = runCatching { URI(rawUri) }.getOrNull()
                val requestToken = extractQueryParameter(uri?.rawQuery, "token")

                if (!RadioTransferRequestPolicy.tokenMatches(secureToken, requestToken)) {
                    sendResponse(output, 403, "Forbidden", "text/plain; charset=utf-8", "인증 토큰이 일치하지 않습니다.")
                    return
                }

                when (method) {
                    "GET" -> {
                        sendResponse(output, 200, "OK", "text/html; charset=utf-8", HTML_PAGE)
                    }
                    "POST" -> {
                        val contentLength = extractContentLength(requestHeader)
                        when (RadioTransferRequestPolicy.payloadDecision(contentLength)) {
                            RadioTransferPayloadDecision.LENGTH_REQUIRED -> {
                                sendResponse(output, 411, "Length Required", "text/plain; charset=utf-8", "파일 크기 정보를 확인할 수 없습니다.")
                                return
                            }
                            RadioTransferPayloadDecision.TOO_LARGE -> {
                                sendResponse(output, 413, "Payload Too Large", "text/plain; charset=utf-8", "파일 크기가 제한(128KB)을 초과했습니다.")
                                return
                            }
                            RadioTransferPayloadDecision.ACCEPT -> Unit
                        }

                        val bodyBytes = readBody(input, contentLength)
                        if (bodyBytes.size > InternetRadioCodec.MAX_PAYLOAD_BYTES) {
                            sendResponse(output, 413, "Payload Too Large", "text/plain; charset=utf-8", "파일 크기가 제한(128KB)을 초과했습니다.")
                            return
                        }

                        when (val result = InternetRadioCodec.decode(bodyBytes)) {
                            is RadioDecodeResult.Success -> {
                                sendResponse(
                                    output,
                                    200,
                                    "OK",
                                    "text/plain; charset=utf-8",
                                    "전송이 완료되었습니다. TV 화면에서 가져오기를 확인해 주세요.",
                                )
                                onSuccess(result.channels)
                                // Immediate shutdown after single successful transfer
                                close()
                            }
                            is RadioDecodeResult.Failure -> {
                                sendResponse(
                                    output,
                                    400,
                                    "Bad Request",
                                    "text/plain; charset=utf-8",
                                    result.message,
                                )
                            }
                        }
                    }
                    else -> {
                        sendResponse(output, 405, "Method Not Allowed", "text/plain; charset=utf-8", "지원하지 않는 메서드입니다.")
                    }
                }
            } catch (_: SocketTimeoutException) {
                // Client timed out
            } catch (e: Exception) {
                // Connection or I/O error
            }
        }
    }

    private fun readHeader(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream()
        var matched = 0
        while (out.size() < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b == -1) break
            out.write(b)
            if ((matched == 0 || matched == 2) && b == '\r'.code) {
                matched++
            } else if ((matched == 1 || matched == 3) && b == '\n'.code) {
                matched++
                if (matched == 4) break
            } else {
                matched = 0
            }
        }
        if (out.size() == 0) return null
        return out.toString("UTF-8")
    }

    private fun readBody(input: BufferedInputStream, contentLength: Int): ByteArray {
        val out = ByteArrayOutputStream()
        if (contentLength > 0) {
            val buffer = ByteArray(4096)
            var totalRead = 0
            while (totalRead < contentLength) {
                val toRead = minOf(buffer.size, contentLength - totalRead)
                val read = input.read(buffer, 0, toRead)
                if (read == -1) break
                out.write(buffer, 0, read)
                totalRead += read
                if (totalRead > InternetRadioCodec.MAX_PAYLOAD_BYTES) break
            }
        } else {
            val buffer = ByteArray(4096)
            while (out.size() <= InternetRadioCodec.MAX_PAYLOAD_BYTES) {
                val read = input.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun sendResponse(
        output: BufferedOutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        bodyText: String,
    ) {
        val bodyBytes = bodyText.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
        output.write(bodyBytes)
        output.flush()
    }

    private fun extractQueryParameter(rawQuery: String?, key: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        return rawQuery.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == key }
            ?.get(1)
    }

    private fun extractContentLength(headers: String): Int {
        for (line in headers.lines()) {
            val lower = line.lowercase()
            if (lower.startsWith("content-length:")) {
                val value = line.substringAfter(":").trim()
                return value.toIntOrNull() ?: 0
            }
        }
        return 0
    }

    override fun close() {
        if (!isRunning.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        scheduler.shutdownNow()
        executor.shutdownNow()
        onClosed()
    }

    companion object {
        const val SESSION_TIMEOUT_SECONDS = 180L
        const val SOCKET_TIMEOUT_MILLIS = 8_000
        const val MAX_HEADER_BYTES = 16 * 1024

        fun generateToken(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun findLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                val candidateList = mutableListOf<String>()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) {
                        continue
                    }
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                            val ip = addr.hostAddress ?: continue
                            // Prefer Wi-Fi or Ethernet
                            val name = networkInterface.name.lowercase()
                            if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")) {
                                return ip
                            }
                            candidateList.add(ip)
                        }
                    }
                }
                return candidateList.firstOrNull()
            } catch (_: Exception) {
                return null
            }
        }

        private const val HTML_PAGE = """<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>S.tand 라디오 전송</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      background: #121516;
      color: #F7F3EA;
      padding: 24px;
      margin: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      box-sizing: border-box;
    }
    .card {
      background: #1E2324;
      border: 1px solid #32383A;
      border-radius: 20px;
      padding: 32px 24px;
      max-width: 440px;
      width: 100%;
      box-sizing: border-box;
      text-align: center;
      box-shadow: 0 10px 30px rgba(0,0,0,0.5);
    }
    h1 { font-size: 22px; margin: 0 0 12px; font-weight: 700; color: #FFA053; }
    p { font-size: 14px; color: #A0AAB0; line-height: 1.5; margin: 0 0 24px; }
    .file-input-wrapper {
      position: relative;
      margin-bottom: 20px;
    }
    .file-btn {
      display: block;
      width: 100%;
      padding: 16px;
      background: #272F32;
      border: 1px dashed #546064;
      border-radius: 12px;
      color: #E2E8F0;
      font-size: 14px;
      cursor: pointer;
      box-sizing: border-box;
    }
    .submit-btn {
      width: 100%;
      padding: 16px;
      background: #E87A38;
      color: #FFFFFF;
      border: none;
      border-radius: 12px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      transition: background 0.2s;
    }
    .submit-btn:disabled { background: #383F42; color: #788286; cursor: not-allowed; }
    #status { margin-top: 20px; font-size: 14px; min-height: 20px; line-height: 1.5; }
    .success { color: #5CD08B; font-weight: 600; font-size: 15px; }
    .error { color: #FF6B6B; font-weight: 500; }
  </style>
</head>
<body>
  <div class="card">
    <h1>S.tand 라디오 전송</h1>
    <p>TV로 전송할 S.tand 라디오 설정 파일(.standradio.json)을 선택해 주세요.</p>
    <div class="file-input-wrapper">
      <input type="file" id="fileInput" accept=".json,.standradio.json,application/json" class="file-btn">
    </div>
    <button id="sendBtn" class="submit-btn" disabled>TV로 보내기</button>
    <div id="status"></div>
  </div>
  <script>
    const fileInput = document.getElementById('fileInput');
    const sendBtn = document.getElementById('sendBtn');
    const status = document.getElementById('status');
    fileInput.addEventListener('change', () => {
      sendBtn.disabled = !fileInput.files.length;
      status.textContent = '';
    });
    sendBtn.addEventListener('click', async () => {
      const file = fileInput.files[0];
      if (!file) return;
      if (file.size > 131072) {
        status.className = 'error';
        status.textContent = '파일 크기가 128KB 제한을 초과했습니다.';
        return;
      }
      sendBtn.disabled = true;
      status.className = '';
      status.textContent = 'TV로 전송 중...';
      try {
        const text = await file.text();
        const res = await fetch(window.location.href, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json; charset=utf-8' },
          body: text
        });
        const responseText = await res.text();
        if (res.ok) {
          status.className = 'success';
          status.textContent = '✓ 전송이 완료되었습니다! TV 화면에서 가져오기를 확인해 주세요.';
          fileInput.disabled = true;
          sendBtn.style.display = 'none';
        } else {
          status.className = 'error';
          status.textContent = responseText || '전송에 실패했습니다.';
          sendBtn.disabled = false;
        }
      } catch (err) {
        status.className = 'error';
        status.textContent = '전송 실패: ' + err.message;
        sendBtn.disabled = false;
      }
    });
  </script>
</body>
</html>"""
    }
}
