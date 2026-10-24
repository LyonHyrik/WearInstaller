package com.lyon.wearinstaller.adb

import dadb.AdbKeyPair
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * 持久化的 ADB 调试密钥。
 *
 * ADB 使用非对称密钥做「调试授权」：设备端保存公钥，host 端保存私钥。
 * 只要私钥不变，设备就不会重复弹出授权确认框 —— 所以密钥必须落盘而不是每次启动重新生成。
 *
 * 密钥的生成、解析与 AUTH 公钥编码全部交给 [AdbKeyPair]：它的公钥文件是 AOSP `adbkey.pub`
 * 同款格式（Android RSAPublicKey 结构体的 base64），adbd 只认这种编码，写成 X.509 PEM
 * 会被 adbd 静默丢弃 —— 表现就是握手卡住直到超时。
 */
class AdbKeyStore(private val directory: File) {

    private val privateKeyFile: File get() = File(directory, "adbkey")
    private val publicKeyFile: File get() = File(directory, "adbkey.pub")

    private var keyPair: AdbKeyPair? = null
    private var privateKey: java.security.interfaces.RSAPrivateKey? = null
    private var createdAt: Long = 0L

    /** 读取或生成密钥对，同步执行。 */
    @Synchronized
    fun ensure(): AdbIdentity {
        loadExisting()
        if (keyPair == null) generate()
        return AdbIdentity(
            fingerprint = fingerprint(),
            publicKeyText = publicKeyText(),
            createdAt = createdAt,
        )
    }

    /** 交给 dadb 做 AUTH 握手用的密钥对。 */
    @Synchronized
    fun adbKeyPair(): AdbKeyPair {
        ensure()
        return keyPair ?: error("密钥尚未初始化")
    }

    @Synchronized
    fun identity(): AdbIdentity = ensure()

    /**
     * AUTH RSAPUBLICKEY 的载荷：adbkey.pub 的原始内容 + 结尾 NUL。
     * dadb 的对应实现是 internal，配对流程只能自己拼。
     */
    @Synchronized
    fun authPublicKeyPayload(): ByteArray {
        ensure()
        val bytes = publicKeyFile.readBytes()
        val payload = ByteArray(bytes.size + 1)
        bytes.copyInto(payload)
        return payload
    }

    /**
     * AUTH SIGNATURE：用私钥对挑战 token 做 PKCS#1 v1.5 + SHA-1 签名。
     * dadb 走的是同一套填充，两者产出的字节完全一致。
     */
    @Synchronized
    fun signToken(token: ByteArray): ByteArray {
        ensure()
        val key = privateKey ?: error("密钥尚未初始化")
        val signature = java.security.Signature.getInstance("SHA1withRSA")
        signature.initSign(key)
        signature.update(token)
        return signature.sign()
    }

    /** 删除本地密钥，下次连接时重新生成（设备端仍保留旧公钥，可在系统里手动撤销）。 */
    @Synchronized
    fun reset() {
        keyPair = null
        privateKeyFile.delete()
        publicKeyFile.delete()
    }

    private fun loadExisting() {
        if (keyPair != null) return
        if (!privateKeyFile.exists() || !publicKeyFile.exists()) return
        try {
            keyPair = AdbKeyPair.read(privateKeyFile, publicKeyFile)
            privateKey = parsePrivateKey(privateKeyFile)
            createdAt = privateKeyFile.lastModified()
            AdbLog.d("已加载既有 ADB 密钥：${fingerprint()}")
        } catch (t: Throwable) {
            // 密钥文件损坏时直接重新生成
            AdbLog.e("既有密钥无法解析，改为重新生成", t)
            keyPair = null
            privateKey = null
        }
    }

    private fun generate() {
        directory.mkdirs()
        // 旧格式（PEM 公钥）无法被 adbd 使用，删掉避免误用
        privateKeyFile.delete()
        publicKeyFile.delete()
        AdbKeyPair.generate(privateKeyFile, publicKeyFile)
        keyPair = AdbKeyPair.read(privateKeyFile, publicKeyFile)
        privateKey = parsePrivateKey(privateKeyFile)
        createdAt = privateKeyFile.lastModified()
        runCatching { privateKeyFile.setReadOnly() }
        AdbLog.i("已生成新的 ADB 密钥：${fingerprint()}")
    }

    /** 解析 PKCS#8 PEM 私钥，配对握手需要自己签名。 */
    private fun parsePrivateKey(file: File): java.security.interfaces.RSAPrivateKey {
        val body = file.readText()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .filterNot { it.isWhitespace() }
        val spec = java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(body))
        return java.security.KeyFactory.getInstance("RSA").generatePrivate(spec)
            as java.security.interfaces.RSAPrivateKey
    }

    /** adbkey.pub 的文本内容，登记到设备上的就是这个字符串。 */
    private fun publicKeyText(): String = runCatching { publicKeyFile.readText().trim() }.getOrDefault("")

    /**
     * 系统「允许 USB 调试吗」弹窗里显示的指纹：SHA1(公钥二进制)。
     * 公钥二进制就是 adbkey.pub 中 base64 部分解码后的内容。
     */
    private fun fingerprint(): String {
        val body = runCatching {
            publicKeyFile.readText()
                .substringBefore(' ')
                .filterNot { it.isWhitespace() }
        }.getOrNull().orEmpty()
        if (body.isEmpty()) return "--"
        val bytes = runCatching { Base64.getDecoder().decode(body) }.getOrNull() ?: return "--"
        return MessageDigest.getInstance("SHA1")
            .digest(bytes)
            .joinToString(":") { "%02X".format(it) }
    }
}

data class AdbIdentity(
    val fingerprint: String,
    val publicKeyText: String,
    val createdAt: Long,
)
