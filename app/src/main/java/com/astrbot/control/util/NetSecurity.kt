package com.astrbot.control.util

import java.net.InetAddress

/** 网络安全辅助：判断地址是否为公网地址，用于风险提示 */
object NetSecurity {

    /**
     * 返回 true 表示该地址是公网地址或未知域名（需要 HTTPS / 强密码）。
     * 私有网段（RFC1918）、回环、链路本地等视为内网。
     */
    fun isPublicAddress(host: String): Boolean {
        val h = host.trim().lowercase().trimEnd('/')
        if (h.isEmpty()) return false
        if (h == "localhost" || h.endsWith(".localhost")) return false
        // 纯 IPv4 判断
        val ipv4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").find(h)?.groupValues
        if (ipv4 != null) {
            val a = ipv4[1].toInt(); val b = ipv4[2].toInt()
            if (a == 127) return false                       // loopback
            if (a == 10) return false                        // 10.0.0.0/8
            if (a == 192 && b == 168) return false            // 192.168.0.0/16
            if (a == 172 && b in 16..31) return false         // 172.16.0.0/12
            if (a == 169 && b == 254) return false            // link-local
            if (a == 0) return false
            if (a >= 224) return false                        // 组播/保留
            return true
        }
        // 域名：一律视为公网（提示使用 HTTPS）
        return h.contains(".")
    }

    /** 是否为内网/回环地址 */
    fun isPrivateAddress(host: String): Boolean = !isPublicAddress(host)
}
