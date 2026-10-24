package com.lyon.wearinstaller.adb

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** 协议版本号，host 与 adbd 都固定使用 0x01000000。 */
internal const val A_VERSION_SEND = 0x01000000

/** 我们向 adbd 声明的单包最大载荷。 */
internal const val MAX_PAYLOAD = 256 * 1024

/**
 * host 发出的 banner，固定 8 字节。
 * adbd 会按 8 字节读取 host banner，因此长度必须恰好为 8。
 */
internal val HOST_BANNER: ByteArray = "host::".toByteArray(Charsets.US_ASCII) + ByteArray(2)

internal object AdbCommand {
    private fun of(name: String): Int =
        (name[0].code and 0xff) or
            ((name[1].code and 0xff) shl 8) or
            ((name[2].code and 0xff) shl 16) or
            ((name[3].code and 0xff) shl 24)

    val SYNC = of("SYNC")
    val CNXN = of("CNXN")
    val AUTH = of("AUTH")
    val OPEN = of("OPEN")
    val OKAY = of("OKAY")
    val CLSE = of("CLSE")
    val WRTE = of("WRTE")

    /** 内部使用的哨兵值，表示连接已断开。 */
    const val CLOSED = -1

    fun name(code: Int): String {
        if (code == CLOSED) return "CLOSED"
        val chars = CharArray(4)
        for (i in 0..3) chars[i] = ((code ushr (i * 8)) and 0xff).toChar()
        return String(chars)
    }
}

internal object AuthType {
    const val OK = 0
    const val TOKEN = 1
    const val SIGNATURE = 2
    const val RSAPUBLICKEY = 3
}

/** ADB 传输层消息：24 字节包头 + 载荷。 */
internal class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val payload: ByteArray,
) {

    fun writeTo(out: OutputStream) {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(payload.size)
        val crc = CRC32()
        crc.update(payload, 0, payload.size)
        header.putInt(crc.value.toInt())
        header.putInt(command xor -1)
        out.write(header.array())
        if (payload.isNotEmpty()) out.write(payload)
    }

    fun text(): String = if (payload.isEmpty()) "" else String(payload, Charsets.UTF_8)

    companion object {
        private const val HEADER_SIZE = 24

        fun readFrom(input: InputStream): AdbMessage {
            val header = ByteArray(HEADER_SIZE)
            try {
                input.readExactly(header)
            } catch (e: IOException) {
                throw AdbException("连接已被设备关闭", e)
            }
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = buffer.getInt(0)
            val arg0 = buffer.getInt(4)
            val arg1 = buffer.getInt(8)
            val length = buffer.getInt(12)
            val magic = buffer.getInt(20)
            if (magic != (command xor -1)) {
                throw AdbException("协议错误：包头校验失败")
            }
            if (length < 0 || length > MAX_PAYLOAD) {
                throw AdbException("协议错误：载荷长度非法 ($length)")
            }
            val payload = ByteArray(length)
            if (length > 0) {
                try {
                    input.readExactly(payload)
                } catch (e: IOException) {
                    throw AdbException("载荷读取中断", e)
                }
            }
            return AdbMessage(command, arg0, arg1, payload)
        }

        private fun InputStream.readExactly(target: ByteArray) {
            var offset = 0
            while (offset < target.size) {
                val read = read(target, offset, target.size - offset)
                if (read < 0) throw IOException("流已提前结束")
                offset += read
            }
        }
    }
}