package dev.orderproof.backend

import com.google.common.truth.Truth.assertThat
import dev.orderproof.backend.domain.PricingEngine
import dev.orderproof.backend.domain.PricingException
import dev.orderproof.backend.domain.roundHalfUp
import dev.orderproof.backend.seed.SeedCatalog
import dev.orderproof.contract.CouponState
import dev.orderproof.contract.ErrorCode
import dev.orderproof.contract.OrderLineRequest
import org.junit.Assert.assertThrows
import org.junit.Test

/** Expected numbers are worked out by hand in docs/behavior-contract.md, not recomputed here. */
class PricingEngineTest {
    private val engine = PricingEngine(825)
    private val now = 1_000_000L
    private val coupons = SeedCatalog.coupons(resetAtMillis = now).associateBy { it.code }

    private val doubleBaconCheddarX2 = OrderLineRequest("ember-smash-burger", listOf("patty-double", "addon-bacon", "addon-cheddar"), 2)
    private val largeFries = OrderLineRequest("charcoal-fries", listOf("fries-large"), 1)

    private fun price(vararg lines: OrderLineRequest, coupon: String? = null, at: Long = now) =
        engine.price(SeedCatalog.menu, lines.toList(), coupon, { coupons[it] }, at)

    @Test
    fun `options and quantity drive line totals`() {
        val cart = price(doubleBaconCheddarX2, largeFries)
        assertThat(cart.lines.map { it.unitPriceMinor }).containsExactly(2020L, 845L).inOrder()
        assertThat(cart.lines.map { it.lineTotalMinor }).containsExactly(4040L, 845L).inOrder()
        assertThat(cart.subtotalMinor).isEqualTo(4885)
        assertThat(cart.taxMinor).isEqualTo(403)
        assertThat(cart.totalMinor).isEqualTo(5288)
    }

    @Test
    fun `percent coupon rounds half up and tax applies after discount`() {
        val cart = price(doubleBaconCheddarX2, largeFries, coupon = " ember10 ")
        assertThat(cart.coupon?.state).isEqualTo(CouponState.APPLIED)
        assertThat(cart.discountMinor).isEqualTo(489)
        assertThat(cart.taxMinor).isEqualTo(363)
        assertThat(cart.totalMinor).isEqualTo(4759)
    }

    @Test
    fun `fixed coupon`() {
        val cart = price(doubleBaconCheddarX2, largeFries, coupon = "WELCOME5")
        assertThat(cart.discountMinor).isEqualTo(500)
        assertThat(cart.totalMinor).isEqualTo(4747)
    }

    @Test
    fun `coupon expires exactly at its expiry instant`() {
        val expiry = now + SeedCatalog.EMBER10_LIFETIME_MILLIS
        assertThat(price(doubleBaconCheddarX2, coupon = "EMBER10", at = expiry - 1).coupon?.state).isEqualTo(CouponState.APPLIED)
        val expired = price(doubleBaconCheddarX2, coupon = "EMBER10", at = expiry)
        assertThat(expired.coupon?.state).isEqualTo(CouponState.EXPIRED)
        assertThat(expired.discountMinor).isEqualTo(0)
    }

    @Test
    fun `coupon minimum and unknown codes give no discount`() {
        val small = OrderLineRequest("burnt-honey-cake", emptyList(), 1)
        assertThat(price(small, coupon = "EMBER10").coupon?.state).isEqualTo(CouponState.MINIMUM_NOT_MET)
        assertThat(price(small, coupon = "FREEFOOD").coupon?.state).isEqualTo(CouponState.NOT_FOUND)
        assertThat(price(small, coupon = "FREEFOOD").discountMinor).isEqualTo(0)
    }

    @Test
    fun `required option groups need exactly one choice`() {
        val error = assertThrows(PricingException::class.java) {
            price(OrderLineRequest("ember-smash-burger", listOf("addon-bacon"), 1))
        }
        assertThat(error.code).isEqualTo(ErrorCode.INVALID_OPTIONS)
        assertThrows(PricingException::class.java) {
            price(OrderLineRequest("ember-smash-burger", listOf("patty-single", "patty-double"), 1))
        }
    }

    @Test
    fun `foreign options, duplicates and bad quantities are rejected`() {
        assertThat(assertThrows(PricingException::class.java) { price(OrderLineRequest("charcoal-fries", listOf("addon-bacon"), 1)) }.code)
            .isEqualTo(ErrorCode.INVALID_OPTIONS)
        assertThat(assertThrows(PricingException::class.java) { price(OrderLineRequest("charcoal-fries", listOf("fries-large", "fries-large"), 1)) }.code)
            .isEqualTo(ErrorCode.INVALID_OPTIONS)
        assertThat(assertThrows(PricingException::class.java) { price(OrderLineRequest("charcoal-fries", listOf("fries-large"), 0)) }.code)
            .isEqualTo(ErrorCode.INVALID_REQUEST)
        assertThat(assertThrows(PricingException::class.java) { price(OrderLineRequest("nope", emptyList(), 1)) }.code)
            .isEqualTo(ErrorCode.UNKNOWN_ITEM)
    }

    @Test
    fun `stock shortfall is reported per item across lines`() {
        val brisket = OrderLineRequest("brisket-sandwich", listOf("bread-brioche"), 3)
        val more = OrderLineRequest("brisket-sandwich", listOf("bread-sourdough"), 3)
        assertThat(price(brisket).unavailableItemIds).isEmpty()
        assertThat(price(brisket, more).unavailableItemIds).containsExactly("brisket-sandwich")
    }

    @Test
    fun `half up rounding`() {
        assertThat(roundHalfUp(165, 10)).isEqualTo(17)
        assertThat(roundHalfUp(164, 10)).isEqualTo(16)
        assertThat(roundHalfUp(200 * 825, 10_000)).isEqualTo(17)
        assertThat(roundHalfUp(0, 10_000)).isEqualTo(0)
    }
}
