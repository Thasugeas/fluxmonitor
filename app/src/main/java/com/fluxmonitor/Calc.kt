package com.fluxmonitor

data class Period(val flux: Double, val eur: Double, val btc: Double)

data class Profit(
    val day: Period,
    val week: Period,
    val month: Period,
    val netDay: Period,
    val netWeek: Period,
    val netMonth: Period
)

object Calc {

    private const val DAYS_PER_MONTH = 30.0

    /**
     * @param paymentsPerDay nombre estimé de paiements par jour (dépend de la taille de la file)
     * @param reward récompense par paiement, en FLUX
     * @param cost coût mensuel (en EUR ou en FLUX selon costInEur)
     */
    fun compute(
        paymentsPerDay: Double,
        reward: Double,
        cost: Double,
        costInEur: Boolean,
        p: Prices
    ): Profit {
        fun mk(fluxAmount: Double) = Period(
            flux = fluxAmount,
            eur = fluxAmount * p.fluxEur,
            btc = fluxAmount * p.fluxBtc
        )

        val fluxDay = paymentsPerDay * reward
        val fluxWeek = fluxDay * 7
        val fluxMonth = fluxDay * DAYS_PER_MONTH

        // Coût converti en FLUX
        val costMonthFlux = if (costInEur) {
            if (p.fluxEur > 0) cost / p.fluxEur else 0.0
        } else cost
        val costDayFlux = costMonthFlux / DAYS_PER_MONTH
        val costWeekFlux = costDayFlux * 7

        return Profit(
            day = mk(fluxDay),
            week = mk(fluxWeek),
            month = mk(fluxMonth),
            netDay = mk(fluxDay - costDayFlux),
            netWeek = mk(fluxWeek - costWeekFlux),
            netMonth = mk(fluxMonth - costMonthFlux)
        )
    }

    /**
     * Estimation du nombre de paiements par jour :
     * 2880 blocs/jour (1 bloc / 30 s). Un nœud est payé une fois
     * tous les (taille de la file) blocs environ.
     */
    fun paymentsPerDay(queueSize: Int?): Double {
        if (queueSize == null || queueSize <= 0) return 0.0
        return 2880.0 / queueSize
    }
}
