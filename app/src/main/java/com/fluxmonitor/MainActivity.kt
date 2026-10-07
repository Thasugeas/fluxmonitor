package com.fluxmonitor

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var nodeInput: EditText
    private lateinit var costInput: EditText
    private lateinit var costFluxBox: CheckBox
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = 32
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Nœud (IP:port)" })
        nodeInput = EditText(this).apply {
            setText(Prefs.getNode(this@MainActivity))
            hint = "ex: 65.21.207.34:16167"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        root.addView(nodeInput)

        root.addView(TextView(this).apply { text = "Coût mensuel" })
        costInput = EditText(this).apply {
            setText(Prefs.getCost(this@MainActivity).toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        root.addView(costInput)

        costFluxBox = CheckBox(this).apply {
            text = "Coût exprimé en FLUX (sinon EUR)"
            isChecked = Prefs.isCostInFlux(this@MainActivity)
        }
        root.addView(costFluxBox)

        val btn = Button(this).apply { text = "Actualiser" }
        root.addView(btn)

        output = TextView(this).apply { textSize = 16f }
        val scroll = ScrollView(this)
        scroll.addView(output, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll)
        setContentView(root)

        btn.setOnClickListener { refresh() }
    }

    private fun refresh() {
        val node = nodeInput.text.toString().trim()
        val cost = costInput.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
        val inFlux = costFluxBox.isChecked
        Prefs.setNode(this, node)
        Prefs.setCost(this, cost)
        Prefs.setCostInFlux(this, inFlux)
        output.text = "Chargement..."

        thread {
            val text = try {
                val info = Api.fetchNode(node)
                val prices = Api.fetchPrices()
                val height = try { Api.fetchHeight() } catch (e: Exception) { 0L }
                val stratus = try { Api.fetchStratusCount() } catch (e: Exception) { 0 }

                val costFlux = if (inFlux) cost else if (prices.fluxEur > 0) cost / prices.fluxEur else 0.0
                val perDay = Calc.fluxPerDay(stratus)
                val confirmed = height > 0 && height - info.lastConfirmedHeight < 200
                val paid = if (info.lastPaidHeight == 0L) "Jamais payé" else "bloc ${info.lastPaidHeight}"
                val eta = Calc.daysToPayment(info.rank)

                val sb = StringBuilder()
                sb.appendLine("Nœud: ${info.ip}")
                sb.appendLine("Tier: ${info.tier}")
                sb.appendLine("Rang: ${info.rank}")
                sb.appendLine("Statut: " + if (height == 0L) "Inconnu" else if (confirmed) "Confirmé" else "Non confirmé récemment")
                sb.appendLine("Dernier paiement: $paid")
                if (eta >= 0) sb.appendLine("Paiement estimé dans ~%.1f jours (indicatif)".format(eta))
                sb.appendLine()
                sb.appendLine("FLUX = %.5f €  |  BTC = %.0f €".format(prices.fluxEur, prices.btcEur))
                sb.appendLine("Stratus actifs: $stratus")
                if (Calc.STRATUS_REWARD_PER_BLOCK == 0.0)
                    sb.appendLine("⚠ Récompense par bloc non renseignée dans Calc.kt")
                sb.appendLine()
                for ((label, days) in listOf("Jour" to 1, "Semaine" to 7, "Mois" to 30)) {
                    val e = Calc.earnings(perDay, days, prices, costFlux)
                    sb.appendLine("$label: %.2f FLUX | %.2f € | %.8f BTC".format(e.flux, e.eur, e.btc))
                }
                sb.toString()
            } catch (e: Exception) {
                "Erreur: ${e.message}"
            }
            runOnUiThread { output.text = text }
        }
    }
}
