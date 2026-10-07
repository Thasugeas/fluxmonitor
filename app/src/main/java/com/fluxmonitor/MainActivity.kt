package com.fluxmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) { Screen() } }
        }
    }
}

private fun f(v: Double, d: Int = 2) = String.format(Locale.FRANCE, "%.${d}f", v)

@Composable
fun Screen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()

    var ip by remember { mutableStateOf(prefs.nodeIp) }
    var reward by remember { mutableStateOf(prefs.rewardPerBlock.toString()) }
    var cost by remember { mutableStateOf(prefs.costValue.toString()) }
    var costEur by remember { mutableStateOf(prefs.costInEur) }

    var node by remember { mutableStateOf<NodeInfo?>(null) }
    var prices by remember { mutableStateOf<Prices?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            prefs.nodeIp = ip.trim()
            prefs.rewardPerBlock = reward.replace(',', '.').toDoubleOrNull() ?: 22.5
            prefs.costValue = cost.replace(',', '.').toDoubleOrNull() ?: 0.0
            prefs.costInEur = costEur
            try {
                prices = Api.fetchPrices()
            } catch (e: Exception) {
                error = "Cours indisponibles : ${e.message}"
            }
            if (ip.isBlank()) {
                node = null
                error = (error ?: "") + "\nSaisis l'IP de ton nœud dans les réglages."
            } else {
                try {
                    node = Api.fetchNode(ip.trim())
                } catch (e: Exception) {
                    error = (error ?: "") + "\nNœud indisponible : ${e.message}"
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Flux Monitor", style = MaterialTheme.typography.headlineMedium)

        // --- Réglages ---
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Réglages", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it },
                    label = { Text("IP du nœud") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reward, onValueChange = { reward = it },
                    label = { Text("Récompense par paiement (FLUX)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cost, onValueChange = { cost = it },
                    label = { Text("Coût mensuel") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = costEur, onClick = { costEur = true }, label = { Text("EUR") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !costEur, onClick = { costEur = false }, label = { Text("FLUX") })
                }
                Button(onClick = { refresh() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) "Chargement..." else "Actualiser")
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        // --- Nœud ---
        node?.let { n ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nœud", style = MaterialTheme.typography.titleMedium)
                    Text("Statut : ${n.status}")
                    Text("Tier : ${n.tier}")
                    Text("Rang dans la file : ${n.rank?.toString() ?: "indisponible"}" +
                        (n.queueSize?.let { " / $it" } ?: ""))
                    Text("Paiement estimé dans : " +
                        (n.etaMinutes?.let { "~${f(it, 0)} min" } ?: "indisponible"))
                }
            }
        }

        // --- Cours ---
        prices?.let { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cours", style = MaterialTheme.typography.titleMedium)
                    Text("FLUX : ${f(p.fluxEur, 4)} €  |  ${String.format(Locale.FRANCE, "%.8f", p.fluxBtc)} BTC")
                    Text("BTC : ${f(p.btcEur, 0)} €")
                }
            }

            // --- Rentabilité ---
            val rewardD = reward.replace(',', '.').toDoubleOrNull() ?: 22.5
            val costD = cost.replace(',', '.').toDoubleOrNull() ?: 0.0
            val ppd = Calc.paymentsPerDay(node?.queueSize)
            val pr = Calc.compute(ppd, rewardD, costD, costEur, p)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Rentabilité (brut)", style = MaterialTheme.typography.titleMedium)
                    Text("Paiements estimés / jour : ${f(ppd, 2)}")
                    PeriodRow("Jour", pr.day)
                    PeriodRow("Semaine", pr.week)
                    PeriodRow("Mois (30 j)", pr.month)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Rentabilité (net, après coût)", style = MaterialTheme.typography.titleMedium)
                    PeriodRow("Jour", pr.netDay)
                    PeriodRow("Semaine", pr.netWeek)
                    PeriodRow("Mois (30 j)", pr.netMonth)
                }
            }
        }
    }
}

@Composable
fun PeriodRow(label: String, p: Period) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text("${f(p.flux)} FLUX  |  ${f(p.eur)} €  |  ${String.format(Locale.FRANCE, "%.6f", p.btc)} BTC")
    }
}
