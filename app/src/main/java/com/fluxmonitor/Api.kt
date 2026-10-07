package com.fluxmonitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class NodeInfo(
    val status: String,
    val tier: String,
    val rank: Int?,          // position dans la file d'attente de paiement
    val queueSize: Int?,     // taille de la file
    val etaMinutes: Double?, // délai estimé avant paiement
    val raw: String
)

data class Prices(val fluxEur: Double, val fluxBtc: Double, val btcEur: Double)

object Api {

    private fun get(url: String, timeout: Int = 15000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "FluxMonitor/1.0")
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun fetchPrices(): Prices = withContext(Dispatchers.IO) {
        val txt = get(
            "https://api.coingecko.com/api/v3/simple/price" +
                "?ids=zelcash,bitcoin&vs_currencies=eur,btc"
        )
        val j = JSONObject(txt)
        val flux = j.getJSONObject("zelcash")
        val btc = j.getJSONObject("bitcoin")
        Prices(
            fluxEur = flux.getDouble("eur"),
            fluxBtc = flux.getDouble("btc"),
            btcEur = btc.getDouble("eur")
        )
    }

    /**
     * Cherche ton nœud dans la liste publique via son IP,
     * puis récupère sa position dans la file de paiement.
     */
    suspend fun fetchNode(ip: String): NodeInfo = withContext(Dispatchers.IO) {
        val listTxt = get("https://api.runonflux.io/daemon/viewdeterministiczelnodelist", 30000)
        val data = JSONObject(listTxt).optJSONArray("data") ?: JSONArray()

        var found: JSONObject? = null
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val nodeIp = o.optString("ip", "")
            if (nodeIp == ip || nodeIp.startsWith("$ip:")) {
                found = o
                break
            }
        }

        if (found == null) {
            return@withContext NodeInfo("Introuvable", "?", null, null, null, "")
        }

        val status = found.optString("status", "?")
        val tier = found.optString("tier", "?")

        // Calcul du rang parmi les nœuds du même tier et confirmés.
        // Les nœuds sont triés par "lastpaidheight" croissant : le plus ancien est payé en premier.
        val sameTier = mutableListOf<JSONObject>()
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            if (o.optString("tier") == tier && o.optString("status") == "ENABLED") {
                sameTier.add(o)
            }
        }
        sameTier.sortBy { it.optLong("last_paid_height", 0L) }
        val queueSize = sameTier.size
        val rankIdx = sameTier.indexOfFirst { it.optString("ip").startsWith(ip) }
        val rank = if (rankIdx >= 0) rankIdx + 1 else null

        // Environ 1 bloc toutes les 30 s ; chaque tier reçoit un paiement par bloc de récompense.
        val eta = if (rank != null) rank * 0.5 else null // en minutes (approximation)

        NodeInfo(status, tier, rank, queueSize, eta, found.toString())
    }
}
