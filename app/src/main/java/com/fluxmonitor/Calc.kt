package com.fluxmonitor

object Calc {
    // À VÉRIFIER sur un explorateur FLUX (valeurs non confirmées)
    const val BLOCKS_PER_DAY = 2880.0          // ~30 s par bloc
    const val STRATUS_REWARD_PER_BLOCK = 0.0   // <-- à renseigner

    data class Earnings(val flux: Double, val eur: Double, val btc: Double)

    /** Gains attendus par jour pour un Stratus. */
    fun fluxPerDay(stratusCount: Int, rewardPerBlock: Double = STRATUS_REWARD_PER_BLOCK): Double {
        if (stratusCount <= 0) return 0.0
        return BLOCKS_PER_DAY * rewardPerBlock / stratusCount
    }

    fun earnings(fluxPerDay: Double, days: Int, p: Prices, costPerMonthFlux: Double): Earnings {
        val grossFlux = fluxPerDay * days
        val netFlux = grossFlux - costPerMonthFlux * days / 30.0
        return Earnings(netFlux, netFlux * p.fluxEur, netFlux * p.fluxBtc)
    }

    /** Estimation grossière du délai avant paiement (rang = nb de nœuds avant vous). */
    fun daysToPayment(rank: Int): Double = if (rank < 0) -1.0 else rank / 720.0 // 720 ≈ paiements/jour, à vérifier
}
