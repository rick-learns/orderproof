package dev.orderproof.emberkitchen.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {
    @Test
    fun formatsMinorUnitsWithoutFloatingPoint() {
        assertThat(formatMoney(0)).isEqualTo("$0.00")
        assertThat(formatMoney(5)).isEqualTo("$0.05")
        assertThat(formatMoney(1402)).isEqualTo("$14.02")
        assertThat(formatMoney(123_456_789)).isEqualTo("$1234567.89")
        assertThat(formatMoney(-489)).isEqualTo("−$4.89")
    }
}
