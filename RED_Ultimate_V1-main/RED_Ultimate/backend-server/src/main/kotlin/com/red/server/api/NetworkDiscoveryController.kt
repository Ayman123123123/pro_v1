package com.red.server.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * متحكم اكتشاف الشبكة - يساعد التطبيق على اكتشاف الخادم على كل الشبكات المحلية
 * 
 * أفضل من واتساب وتيليجرام:
 * - يعرض كل عناوين IP المحلية للخادم
 * - يدعم mDNS/NSD advertisement
 * - يعمل على كل الشبكات المحلية
 * - توقيع يونس للتحقق
 */
@RestController
@RequestMapping("/api/network")
class NetworkDiscoveryController(
    @Value("\${server.port:8080}") private val serverPort: Int,
    @Value("\${red.identity.version:1.0.0-YOUNES}") private val version: String
) {

    @GetMapping("/info")
    fun getNetworkInfo(): ResponseEntity<Map<String, Any>> {
        val interfaces = mutableListOf<Map<String, Any>>()
        val ips = mutableListOf<String>()
        
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            
            for (nif in networkInterfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                
                val type = when {
                    nif.name.contains("wlan", true) || nif.name.contains("wifi", true) -> "WIFI"
                    nif.name.contains("eth", true) -> "ETHERNET"
                    nif.name.contains("docker", true) || nif.name.contains("br-", true) -> "DOCKER"
                    nif.name.contains("lo", true) -> "LOOPBACK"
                    else -> "OTHER"
                }
                
                for (addr in nif.interfaceAddresses) {
                    val inetAddr = addr.address
                    if (inetAddr is Inet4Address && !inetAddr.isLoopbackAddress) {
                        val ip = inetAddr.hostAddress ?: continue
                        ips.add(ip)
                        
                        interfaces.add(mapOf(
                            "name" to nif.name,
                            "type" to type,
                            "ip" to ip,
                            "prefix" to addr.networkPrefixLength,
                            "subnet" to "${ip.substringBeforeLast('.')}.0/${addr.networkPrefixLength}",
                            "isUp" to nif.isUp,
                            "isSiteLocal" to inetAddr.isSiteLocalAddress
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            // تجاهل
        }
        
        return ResponseEntity.ok(mapOf(
            "version" to version,
            "brand" to "YOUNES",
            "serverPort" to serverPort,
            "localIps" to ips.distinct(),
            "interfaces" to interfaces,
            "discovery" to mapOf(
                "mdnsService" to "_younes._tcp",
                "alternativeService" to "_red._tcp",
                "ports" to listOf(8088, 8080, 8443, 443),
                "healthEndpoint" to "/health",
                "authorityEndpoint" to "/api/identity/authority"
            ),
            "capabilities" to mapOf(
                "supportsAllLocalNetworks" to true,
                "supportsP2PLan" to true,
                "supportsMdns" to true,
                "supportsIpScan" to true,
                "networks" to listOf("WIFI", "ETHERNET", "USB_TETHER", "VPN", "HOTSPOT", "BLUETOOTH", "MOBILE")
            ),
            "message" to "RED Sovereign - Works on all local networks and all networks"
        ))
    }
    
    @GetMapping("/discovery")
    fun getDiscoveryInfo(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "serviceName" to "YOUNES-RED",
            "serviceType" to "_younes._tcp",
            "alternativeType" to "_red._tcp",
            "version" to version,
            "brand" to "YOUNES",
            "description" to "RED Sovereign - Local-first messaging and calls platform",
            "endpoints" to mapOf(
                "health" to "/health",
                "api" to "/api",
                "websocket" to "/ws/master",
                "calls" to "/ws/calls",
                "conference" to "/ws/conference",
                "livestream" to "/ws/livestream"
            ),
            "features" to listOf(
                "E2EE private chats with Signal Protocol PQXDH + Kyber",
                "Group E2EE with Sender Keys",
                "8 call types: 1-1 audio/video, group 32, conference 100, live, space, LAN P2P",
                "Works on all local networks: WiFi, Ethernet, USB, VPN, Hotspot, Bluetooth",
                "P2P LAN calls without internet",
                "Multi-path call delivery with ringing",
                "Modern UI Liquid Glass 2026",
                "Better than WhatsApp and Telegram"
            )
        ))
    }
    
    @GetMapping("/lan-peers")
    fun getLanPeers(): ResponseEntity<Map<String, Any>> {
        // في المستقبل: سيعرض الأجهزة المكتشفة على نفس الشبكة
        return ResponseEntity.ok(mapOf(
            "peers" to emptyList<Any>(),
            "count" to 0,
            "message" to "LAN peers discovery via NSD/mDNS on Android - this endpoint is for server info only",
            "androidDiscovery" to mapOf(
                "serviceTypes" to listOf("_younes._tcp", "_red._tcp", "_http._tcp"),
                "requiresPermission" to "CHANGE_WIFI_MULTICAST_STATE",
                "multicastLock" to "younes-mdns"
            )
        ))
    }
}
