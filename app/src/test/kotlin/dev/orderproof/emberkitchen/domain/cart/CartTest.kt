package dev.orderproof.emberkitchen.domain.cart

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CartTest {
    private fun line(id: String, item: String, options: List<String>, qty: Int) = CartLine(id, item, item, options, options, qty)

    @Test
    fun identicalCustomizationsMergeRegardlessOfOptionOrder() {
        val cart = Cart()
            .add(line("a", "burger", listOf("bacon", "patty-double"), 1))
            .add(line("b", "burger", listOf("patty-double", "bacon"), 2))
        assertThat(cart.lines).hasSize(1)
        assertThat(cart.lines.single().quantity).isEqualTo(3)
    }

    @Test
    fun differentCustomizationsStaySeparate() {
        val cart = Cart().add(line("a", "burger", listOf("patty-single"), 1)).add(line("b", "burger", listOf("patty-double"), 1))
        assertThat(cart.toOrderLines().map { it.optionIds }).containsExactly(listOf("patty-single"), listOf("patty-double")).inOrder()
        assertThat(cart.itemCount).isEqualTo(2)
    }

    @Test
    fun quantityZeroRemovesTheLine() {
        val cart = Cart().add(line("a", "fries", emptyList(), 2)).setQuantity("a", 0)
        assertThat(cart.lines).isEmpty()
    }
}
