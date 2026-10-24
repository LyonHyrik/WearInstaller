package com.lyon.wearinstaller.adb

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 无线调试「配对」：dadb 只覆盖已连接通道，不含配对握手，所以这里自己实现。
 *
 * 流程与 AOSP 一致：CNXN（banner 为 `host::`）→ 认证 → 打开 `host:pair:<code>`
 * → 写入公钥 → 读到 adbd 返回的错误串（成功时为空）。
 */
internal object AdbPairer {

    fun pair(host: String, port: Int, code: String, keyStore: AdbKeyStore) {
        AdbLog.d("配对 $host:$port")
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(normalized(host), port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = SOCKET_TIMEOUT_MS

            val out = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // 配对连接的 banner 里带上公钥，adbd 用它登记到待授权列表
            AdbMessage(AdbCommand.CNXN, A_VERSION_SEND, MAX_PAYLOAD, HOST_BANNER).writeTo(out)
            out.flush()

            val online = authenticate(input, out, keyStore)
            AdbLog.i("配对通道已建立：$online")

            val localId = 1
            AdbMessage(AdbCommand.OPEN, localId, 0, "host:pair:$code".toByteArray(Charsets.UTF_8))
                .writeTo(out)
            out.flush()
            val remoteId = expectOkay(input, "host:pair:$code")

            AdbMessage(AdbCommand.WRTE, localId, remoteId, keyStore.authPublicKeyPayload())
                .writeTo(out)
            out.flush()

            expectOkay(input, "publickey")

            val response = readUntilClose(input, MAX_RESPONSE_BYTES).trim()
            if (response.isNotEmpty()) {
                throw AdbException("配对失败：$response", hint = AdbException.Hint.PROTOCOL)
            }
            AdbLog.i("配对成功")
        }
    }

    private fun authenticate(input: DataInputStream, out: DataOutputStream, keyStore: AdbKeyStore): String {
        val deadline = System.currentTimeMillis() + AUTH_TIMEOUT_MS
        var sentPublicKey = false
        while (System.currentTimeMillis() < deadline) {
            val message = AdbMessage.readFrom(input)
            when (message.command) {
                AdbCommand.CNXN -> return message.text()

                AdbCommand.AUTH -> when (message.arg0) {
                    AuthType.TOKEN -> {
                        val signature = keyStore.signToken(message.payload)
                        AdbMessage(AdbCommand.AUTH, AuthType.SIGNATURE, 0, signature).writeTo(out)
                        out.flush()
                    }

                    AuthType.RSAPUBLICKEY -> {
                        if (!sentPublicKey) {
                            sentPublicKey = true
                            AdbMessage(AdbCommand.AUTH, AuthType.RSAPUBLICKEY, 0, keyStore.authPublicKeyPayload())
                                .writeTo(out)
                            out.flush()
                        }
                    }

                    else -> throw AdbException(
                        "配对遇到不支持的认证方式：${message.arg0}",
                        hint = AdbException.Hint.PROTOCOL,
                    )
                }

                else -> throw AdbException(
                    "配对时收到意外消息：${AdbCommand.name(message.command)}",
                    hint = AdbException.Hint.PROTOCOL,
                )
            }
        }
        throw AdbException("配对超时：设备没有响应", hint = AdbException.Hint.CONNECTION)
    }

    private fun expectOkay(input: DataInputStream, service: String): Int {
        val message = AdbMessage.readFrom(input)
        when (message.command) {
            AdbCommand.OKAY -> return message.arg1
            AdbCommand.CLSE -> throw AdbException(
                "设备拒绝打开 $service",
                hint = AdbException.Hint.PROTOCOL,
            )

            else -> throw AdbException(
                "打开 $service 时收到意外消息：${AdbCommand.name(message.command)}",
                hint = AdbException.Hint.PROTOCOL,
            )
        }
    }

    private fun readUntilClose(input: DataInputStream, limit: Int): String {
        val buffer = StringBuilder()
        while (true) {
            val message = try {
                AdbMessage.readFrom(input)
            } catch (e: IOException) {
                break
            }
            when (message.command) {
                AdbCommand.WRTE -> buffer.append(message.text())
                AdbCommand.CLSE -> return buffer.toString()
                AdbCommand.OKAY -> Unit
                else -> throw AdbException(
                    "配对时收到意外消息：${AdbCommand.name(message.command)}",
                    hint = AdbException.Hint.PROTOCOL,
                )
            }
            if (buffer.length > limit) break
        }
        return buffer.toString()
    }

    private fun normalized(host: String) = if (host.equals("localhost", ignoreCase = true)) "127.0.0.1" else host

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val SOCKET_TIMEOUT_MS = 30_000
    private const val AUTH_TIMEOUT_MS = 30_000L
    private const val MAX_RESPONSE_BYTES = 4096
}
