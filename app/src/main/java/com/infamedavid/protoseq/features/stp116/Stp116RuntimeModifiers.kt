package com.infamedavid.protoseq.features.stp116

import kotlin.math.roundToInt

fun shouldDropStp116Gate(
    bernoulliDropChance: Float,
    randomFloatProvider: () -> Float,
): Boolean {
    val chance = bernoulliDropChance.coerceIn(0f, 1f)
    if (chance <= 0f) return false
    if (chance >= 1f) return true
    return randomFloatProvider().coerceIn(0f, 1f) < chance
}

fun calculateStp116FinalVelocity(
    velocity: Int,
    randomVelocityAmount: Float,
    randomIntProvider: (minInclusive: Int, maxInclusive: Int) -> Int,
): Int {
    val safeVelocity = velocity.coerceIn(STP_116_MIN_VELOCITY, STP_116_MAX_VELOCITY)
    val amount = randomVelocityAmount.coerceIn(0f, 1f)

    if (amount <= 0f) return safeVelocity

    val spread = (safeVelocity * amount).roundToInt()
    val minVelocity = (safeVelocity - spread).coerceAtLeast(STP_116_MIN_VELOCITY)
    val maxVelocity = (safeVelocity + spread).coerceAtMost(STP_116_MAX_VELOCITY)

    if (minVelocity >= maxVelocity) return minVelocity

    return randomIntProvider(minVelocity, maxVelocity).coerceIn(
        STP_116_MIN_VELOCITY,
        STP_116_MAX_VELOCITY,
    )
}

fun calculateStp116FinalGateLengthTicks(
    gateLengthTicks: Int,
    gateDelayTicks: Int,
    randomGateLengthAmount: Float,
    randomIntProvider: (minInclusive: Int, maxInclusive: Int) -> Int,
): Long {
    val safeDelay = gateDelayTicks.coerceIn(0, STP_116_MAX_GATE_DELAY_TICKS)
    val maxGate = maxStp116GateLengthForDelay(safeDelay)
    val safeGateLength = gateLengthTicks.coerceIn(
        STP_116_MIN_GATE_LENGTH_TICKS,
        maxGate,
    )
    val amount = randomGateLengthAmount.coerceIn(0f, 1f)

    if (amount <= 0f) return safeGateLength.toLong()

    val availableVariation = (maxGate - STP_116_MIN_GATE_LENGTH_TICKS).coerceAtLeast(0)
    val randomWindow = (availableVariation * amount).roundToInt()
    val minGate = (safeGateLength - randomWindow).coerceAtLeast(STP_116_MIN_GATE_LENGTH_TICKS)
    val maxGateRandom = (safeGateLength + randomWindow).coerceAtMost(maxGate)

    if (minGate >= maxGateRandom) return minGate.toLong()

    return randomIntProvider(minGate, maxGateRandom)
        .coerceIn(STP_116_MIN_GATE_LENGTH_TICKS, maxGate)
        .toLong()
}
