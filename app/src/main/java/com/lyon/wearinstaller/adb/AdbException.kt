package com.lyon.wearinstaller.adb

/**
 * ADB 调用过程中出现的可读错误。
 * [hint] 用于判断是否应当引导用户去开发者选项开启无线调试。
 */
class AdbException(
    message: String,
    cause: Throwable? = null,
    val hint: Hint = Hint.UNKNOWN,
) : Exception(message, cause) {

    enum class Hint {
        /** 端口无法连接：无线调试大概率没有开启。 */
        WIRELESS_DEBUG_OFF,

        /** 调试授权被拒：需要在手表上确认「允许调试」。 */
        UNAUTHORIZED,

        /** TCP 层连不上或中途断开。 */
        CONNECTION,

        /** 服务不可用或协议不兼容。 */
        PROTOCOL,

        UNKNOWN,
    }
}