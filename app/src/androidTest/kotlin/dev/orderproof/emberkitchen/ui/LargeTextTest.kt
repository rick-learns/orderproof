package dev.orderproof.emberkitchen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.orderproof.emberkitchen.ui.checkout.CheckoutContent
import dev.orderproof.emberkitchen.ui.theme.EmberTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 200% text in a 360×640 dp window: every control stays reachable and at least 48 dp tall. */
@RunWith(AndroidJUnit4::class)
class LargeTextTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun checkoutStaysUsableAtDoubleTextSize() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                Box(Modifier.requiredSize(360.dp, 640.dp)) {
                    EmberTheme {
                        CheckoutContent(
                            UiFixtures.ready(UiFixtures.twoBurgersCouponExpired).copy(acceptedTotalMinor = 2523, changedFromMinor = 2523),
                            RecordingActions().actions,
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag(TestTags.CONFIRM_NEW_TOTAL).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(TestTags.SUMMARY_TOTAL).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TestTags.PLACE_ORDER).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
}
