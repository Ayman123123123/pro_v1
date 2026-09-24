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
 * يعرض عناوين الشبكة المرئية للخادم ومسارات API المتاحة؛ لا يوفّر
 * هذا المتحكم إعلان mDNS أو خادماً لاتصالات P2P مستقلاً بذاته.
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
                "supportsAllLocalNetworks" to false, // not certified by a discovery handshake
                "supportsP2PLan" to false, // no authenticated server-side P2P handler
                "supportsMdns" to false, // this endpoint does not advertise a service
                "supportsIpScan" to false, // /lan-peers currently returns no discovered peers
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
                "Individual audio/video, group and conference signaling",
                "Live streams and audio spaces",
                "Network discovery information for authenticated clients"
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
