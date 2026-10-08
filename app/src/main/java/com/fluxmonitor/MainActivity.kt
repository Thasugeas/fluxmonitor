package com.fluxmonitor

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var nodeInput: EditText
    private lateinit var costInput: EditText
    private lateinit var costFluxSwitch: CheckBox
    private lateinit var infoText: TextView
    private lateinit var rankText: TextView
    private lateinit var rankBar: ProgressBar
    private lateinit var etaText: TextView
    private lateinit var priceText: TextView
    private lateinit var table: TableLayout
    private lateinit var errorText: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        nodeInput = EditText(this).apply {
            hint = "IP:port du nœud"
            setText(Prefs.getNode(this@MainActivity))
        }
        costInput = EditText(this).apply {
            hint = "Coût mensuel"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            val c = Prefs.getCost(this@MainActivity)
            if (c > 0) setText(c.toString())
        }
        costFluxSwitch = CheckBox(this).apply {
            text = "Coût exprimé en FLUX (sinon EUR)"
            isChecked = Prefs.isCostInFlux(this@MainActivity)
        }
        val button = Button(this).apply {
            text = "Actualiser"
            setOnClickListener { refresh() }
        }

        infoText = TextView(this).apply { textSize = 16f }
        rankText = TextView(this).apply { textSize = 16f; setPadding(0, dp(8), 0, dp(4)) }
        rankBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
        }
        etaText = TextView(this).apply { textSize = 16f; setPadding(0, dp(8), 0, 0) }
        priceText = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(12)) }
        table = TableLayout(this).apply { isStretchAllColumns = true }
        errorText = TextView(this).apply { textSize = 14f; setTextColor(0xFFCC0000.toInt()) }

        root.addView(nodeInput)
        root.addView(costInput)
        root.addView(costFluxSwitch)
        root.addView(button)
        root.addView(infoText)
        root.addView(rankText)
        root.addView(rankBar)
        root.addView(etaText)
        root.addView(priceText)
        root.addView(table)
        root.addView(errorText)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        refresh()
    }

    private fun cell(text: String, bold: Boolean = false, right: Boolean = true): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.MONOSPACE
            gravity = if (right) Gravity.END else Gravity.START
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

    private fun formatEta(days: Double): String {
        val minutes = (days * 24 * 60).toLong()
        return if (minutes < 60) "$minutes min" else "%.1f h".format(minutes / 60.0)
    }

    private fun refresh() {
        val node = nodeInput.text.toString().trim()
        val cost = costInput.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
        val inFlux = costFluxSwitch.isChecked
        Prefs.setNode(this, node)
        Prefs.setCost(this, cost)
        Prefs.setCostInFlux(this, inFlux)

        errorText.text = ""
        thread {
            try {
                val info = Api.fetchNode(node)
                val prices = Api.fetchPrices()
                val height = try { Api.fetchHeight() } catch (e: Exception) { 0L }
                val stratus = Api.fetchStratusCount()

                val costFlux = if (inFlux) cost else if (prices.fluxEur > 0) cost / prices.fluxEur else 0.0
                val perDay = Calc.fluxPerDay(stratus)
                val confirmed = height > 0 && height - info.lastConfirmedHeight < 200
                val paid = if (info.lastPaidHeight == 0L) "Jamais payé" else "bloc ${info.lastPaidHeight}"
                val eta = Calc.daysToPayment(info.rank)

                val info1 = buildString {
                    appendLine("Nœud: ${info.ip}")
                    appendLine("Tier: ${info.tier}")
                    appendLine("Statut: " + if (height == 0L) "Inconnu" else if (confirmed) "Confirmé" else "Non confirmé récemment")
                    append("Dernier paiement: $paid")
                }
                val rankLabel = "Rang: ${info.rank} / $stratus"
                val rankProgress = if (stratus > 0)
                    (1000 - (info.rank.toLong() * 1000 / stratus)).toInt().coerceIn(0, 1000) else 0
                val etaLabel = if (eta >= 0) "Paiement estimé dans ~${formatEta(eta)} (indicatif)" else ""
                val priceLabel = buildString {
                    append("FLUX = %.5f €  |  BTC = %.0f €".format(prices.fluxEur, prices.btcEur))
                    if (Calc.STRATUS_REWARD_PER_BLOCK == 0.0)
                        append("\n⚠ Récompense par bloc non renseignée dans Calc.kt")
                }

                val rows = listOf("Jour" to 1, "Semaine" to 7, "Mois" to 30).map { (label, days) ->
                    val e = Calc.earnings(perDay, days, prices, costFlux)
                    listOf(label, "%.2f".format(e.flux), "%.2f".format(e.eur), "%.8f".format(e.btc))
                }

                runOnUiThread {
                    infoText.text = info1
                    rankText.text = rankLabel
                    rankBar.progress = rankProgress
                    etaText.text = etaLabel
                    priceText.text = priceLabel
                    table.removeAllViews()
                    val head = TableRow(this)
                    head.addView(cell("", bold = true, right = false))
                    head.addView(cell("FLUX", bold = true))
                    head.addView(cell("EUR", bold = true))
                    head.addView(cell("BTC", bold = true))
                    table.addView(head)
                    for (r in rows) {
                        val tr = TableRow(this)
                        tr.addView(cell(r[0], right = false))
                        tr.addView(cell(r[1]))
                        tr.addView(cell(r[2]))
                        tr.addView(cell(r[3]))
                        table.addView(tr)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { errorText.text = "Erreur: ${e.message}" }
            }
        }
    }
}
