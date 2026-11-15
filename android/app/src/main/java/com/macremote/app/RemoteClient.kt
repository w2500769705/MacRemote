package com.macremote.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

const val PORT = 47800

data class FoundMac(val ip: String, val name: String)

/** 发 UDP 指令给 Mac 上的 macremoted。所有网络操作都在单独线程里做。 */
class RemoteClient {
    private val sendExec = Executors.newSingleThreadExecutor()
    private val socket = DatagramSocket()

    @Volatile var host: String = ""

    fun send(cmd: String) {
        val h = host
        if (h.isBlank()) return
        sendExec.execute {
            runCatching {
                val data = cmd.toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(data, data.size, InetAddress.getByName(h), PORT))
            }
        }
    }

    /** 对当前 host 发 ping，返回电脑名；不通返回 null。阻塞调用，放 IO 线程里用。 */
    fun ping(timeoutMs: Int = 800): String? {
        val h = host
        if (h.isBlank()) return null
        return runCatching {
            DatagramSocket().use { s ->
                s.soTimeout = timeoutMs
                val data = "ping".toByteArray()
                s.send(DatagramPacket(data, data.size, InetAddress.getByName(h), PORT))
                val buf = ByteArray(256)
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                parsePong(String(p.data, 0, p.length, Charsets.UTF_8))
            }
        }.getOrNull()
    }

    fun close() {
        sendExec.shutdown()
        socket.close()
    }

    companion object {
        fun parsePong(s: String): String? =
            if (s.startsWith("pong")) s.removePrefix("pong").trim().ifEmpty { "Mac" } else null

        /** 局域网广播找 Mac，阻塞约 timeoutMs。 */
        fun discover(timeoutMs: Int = 1500): List<FoundMac> {
            val found = linkedMapOf<String, FoundMac>()
            runCatching {
                DatagramSocket().use { s ->
                    s.broadcast = true
                    s.soTimeout = 300
                    val data = "ping".toByteArray()
                    repeat(2) {
                        s.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), PORT))
                    }
                    val end = System.currentTimeMillis() + timeoutMs
                    val buf = ByteArray(256)
                    while (System.currentTimeMillis() < end) {
                        val p = DatagramPacket(buf, buf.size)
                        try {
                            s.receive(p)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        val name = parsePong(String(p.data, 0, p.length, Charsets.UTF_8)) ?: continue
                        val ip = p.address.hostAddress ?: continue
                        found[ip] = FoundMac(ip, name)
                    }
                }
            }
            return found.values.toList()
        }
    }
}
