package dev.orderproof.emberkitchen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.orderproof.emberkitchen.domain.checkout.CheckoutState
import dev.orderproof.emberkitchen.domain.checkout.RetryReason
import dev.orderproof.emberkitchen.ui.checkout.CheckoutContent
import dev.orderproof.emberkitchen.ui.checkout.CheckoutUiState
import dev.orderproof.emberkitchen.ui.theme.EmberTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckoutContentTest {
    @get:Rule val compose = createComposeRule()
    private val recorder = RecordingActions()

    private fun show(state: CheckoutUiState) = compose.setContent { EmberTheme { CheckoutContent(state, recorder.actions) } }

    @Test
    fun aReadyQuoteShowsTheServerTotalOnTheButton() {
        show(UiFixtures.ready(UiFixtures.singleBurger))
        compose.onNodeWithTag(TestTags.SUMMARY_TOTAL).assertTextContains("$14.02")
        compose.onNodeWithTag(TestTags.PLACE_ORDER).assertIsEnabled().assertTextContains("Place order · $14.02").performClick()
        assertThat(recorder.calls).containsExactly("place")
    }

    @Test
    fun aChangedTotalBlocksPlacementUntilTheCustomerConfirmsIt() {
        val quote = UiFixtures.twoBurgersCouponExpired
        show(UiFixtures.ready(quote).copy(acceptedTotalMinor = 2523, changedFromMinor = 2523))
        compose.onNodeWithTag(TestTags.PLACE_ORDER).assertIsNotEnabled()
        compose.onNodeWithText("It was $25.23 and is now $28.04", substring = true).assertIsDisplayed()
        compose.onNodeWithText("EMBER10 has expired.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.CONFIRM_NEW_TOTAL).assertTextContains("Confirm new total $28.04").performClick()
        assertThat(recorder.calls).containsExactly("confirmNewTotal")
    }

    @Test
    fun anUnknownOutcomeOffersASafeRetryAndNothingElse() {
        val state = UiFixtures.ready(UiFixtures.singleBurger)
            .copy(submission = CheckoutState.NeedsRetry(UiFixtures.attempt, RetryReason.OUTCOME_UNKNOWN))
        show(state)
        compose.onNodeWithText("you won't be charged twice", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.PLACE_ORDER).assertIsNotEnabled()
        compose.onNodeWithTag(TestTags.CHECKOUT_RETRY).performClick()
        assertThat(recorder.calls).containsExactly("retry")
    }

    @Test
    fun anOutageSaysNothingWasPlaced() {
        show(UiFixtures.ready(UiFixtures.singleBurger).copy(submission = CheckoutState.NeedsRetry(UiFixtures.attempt, RetryReason.SERVICE_UNAVAILABLE)))
        compose.onNodeWithText("Ember Kitchen is unavailable").assertIsDisplayed()
        compose.onNodeWithText("Your order wasn't placed and you weren't charged", substring = true).assertIsDisplayed()
    }

    @Test
    fun whileSubmittingTheCustomerCannotLeaveOrTapAgain() {
        show(UiFixtures.ready(UiFixtures.singleBurger).copy(submission = CheckoutState.Submitting(UiFixtures.attempt)))
        compose.onNodeWithTag(TestTags.SUBMITTING).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.PLACE_ORDER).assertIsNotEnabled().assertTextContains("Placing order…")
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun aSoldOutRejectionLeadsBackToTheCart() {
        show(UiFixtures.ready(UiFixtures.singleBurger).copy(rejection = "Oak-Smoked Brisket Sandwich just sold out. You have not been charged."))
        compose.onNodeWithText("Order not placed").assertIsDisplayed()
        compose.onNodeWithTag(TestTags.BACK_TO_CART).performScrollTo().performClick()
        assertThat(recorder.calls).containsExactly("backToCart")
    }

    @Test
    fun aPricingFailureIsRecoverable() {
        show(CheckoutUiState(quote = dev.orderproof.emberkitchen.ui.cart.QuoteLoad.Failed("Could not connect")))
        compose.onNodeWithText("Couldn't price your order").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertThat(recorder.calls).containsExactly("refreshQuote")
        compose.onNodeWithTag(TestTags.PLACE_ORDER).assertIsNotEnabled()
    }
}
