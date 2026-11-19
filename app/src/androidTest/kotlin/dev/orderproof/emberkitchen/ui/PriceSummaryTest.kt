package dev.orderproof.emberkitchen.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.orderproof.emberkitchen.ui.components.PriceSummary
import dev.orderproof.emberkitchen.ui.theme.EmberTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PriceSummaryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun eachRowReadsAsOnePhraseWithTheServerAmounts() {
        compose.setContent { EmberTheme { PriceSummary(UiFixtures.twoBurgersWithCoupon) } }
        compose.onNodeWithTag(TestTags.SUMMARY_SUBTOTAL).assertTextEquals("Subtotal", "$25.90")
        compose.onNodeWithTag(TestTags.SUMMARY_DISCOUNT).assertTextEquals("Discount (EMBER10)", "−$2.59")
        compose.onNodeWithTag(TestTags.SUMMARY_TAX).assertTextEquals("Tax (8.25%)", "$1.92")
        compose.onNodeWithTag(TestTags.SUMMARY_TOTAL).assertTextEquals("Total", "$25.23")
    }

    @Test
    fun noDiscountRowWithoutADiscount() {
        compose.setContent { EmberTheme { PriceSummary(UiFixtures.twoBurgersCouponExpired) } }
        compose.onNodeWithTag(TestTags.SUMMARY_DISCOUNT).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.SUMMARY_TOTAL).assertTextEquals("Total", "$28.04")
    }
}
