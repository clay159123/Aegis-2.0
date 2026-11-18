package com.example.domain

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Item 47: one switch for how fast the game runs. Every timer (missions, schools, building, recovery)
 * and passive income reads it, so playtesting can stay fast and release can run slower without touching
 * each number. Testing keeps today's speeds. Release numbers are tuned in Session 19.
 */
enum class TimeMode(
    val label: String,
    /** Multiplier on every duration (2.0 = everything takes twice as long). */
    val durationMultiplier: Double,
    /** Multiplier on passive income per second. */
    val incomeMultiplier: Double
) {
    TESTING("Testing", 1.0, 1.0),
    RELEASE("Release", 1.0, 1.0) // set in Session 19
}

object GameTiming {
    /** Testing by default; the Settings menu can switch it (applies on the next app start). */
    @Volatile
    var MODE: TimeMode = TimeMode.TESTING

    fun seconds(base: Int): Int = (base * MODE.durationMultiplier).roundToInt().coerceAtLeast(1)
    fun seconds(base: Long): Long = (base * MODE.durationMultiplier).roundToLong().coerceAtLeast(1L)
    fun millis(base: Long): Long = (base * MODE.durationMultiplier).roundToLong()
    fun income(perSecond: Double): Double = perSecond * MODE.incomeMultiplier
}
