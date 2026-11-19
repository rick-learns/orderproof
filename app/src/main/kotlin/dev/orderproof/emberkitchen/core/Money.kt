package dev.orderproof.emberkitchen.core

/** Formats integer minor units as USD without any floating point: 1402 -> "$14.02". */
fun formatMoney(minor: Long): String {
    val sign = if (minor < 0) "−" else ""
    val abs = kotlin.math.abs(minor)
    return "$sign$${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
