package com.fluxmonitor

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class NodeInfo(
    val ip: String,
    val tier: String,
    val rank: Int,
    val lastPaidHeight: Long,
    val lastConfirmedHeight: Long,
    val amount: Double
)

data class Prices(val fluxEur: Double, val btcEur: Double, val fluxBtc: Double)

object Api {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "FluxMonitor")
        try {
            if (c.responseCode != 200) throw Exception("HTTP ${c.responseCode} sur $url")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Cherche le nœud dont "ip" correspond exactement à ipPort (ex: 65.21.207.34:16167). */
    fun fetchNode(ipPort: String): NodeInfo {
        val target = ipPort.trim()
        if (target.isEmpty()) throw Exception("Adresse du nœud non renseignée")
        val root = JSONObject(get("https://api.runonflux.io/daemon/viewdeterministicfluxnodelist"))
        val arr = root.getJSONArray("data")
        val found = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ip = o.optString("ip")
            if (ip.startsWith(target.substringBefore(":"))) found.add(ip)
            if (ip == target) {
                return NodeInfo(
                    ip = ip,
                    tier = o.optString("tier"),
                    rank = o.optInt("rank", -1),
                    lastPaidHeight = o.optLong("last_paid_height", 0),
                    lastConfirmedHeight = o.optLong("last_confirmed_height", 0),
                    amount = o.optString("amount", "0").toDoubleOrNull() ?: 0.0
                )
            }
        }
        throw Exception("Nœud $target introuvable. Trouvés sur cette IP: ${found.joinToString()}")
    }

    fun fetchHeight(): Long {
        val root = JSONObject(get("https://api.runonflux.io/daemon/getblockcount"))
        return root.optLong("data", 0)
    }

    fun fetchStratusCount(): Int {
        val root = JSONObject(get("https://api.runonflux.io/daemon/getfluxnodecount"))
        val data = root.getJSONObject("data")
        val n = data.optInt("stratus-enabled", -1)
        if (n <= 0) throw Exception("Nombre de Stratus introuvable dans la réponse de l'API")
        return n
    }

    fun fetchPrices(): Prices {
        val root = JSONObject(
            get("https://api.coingecko.com/api/v3/simple/price?ids=zelcash,bitcoin&vs_currencies=eur,btc")
        )
        val flux = root.getJSONObject("zelcash")
        val btc = root.getJSONObject("bitcoin")
        return Prices(flux.getDouble("eur"), btc.getDouble("eur"), flux.getDouble("btc"))
    }
}
