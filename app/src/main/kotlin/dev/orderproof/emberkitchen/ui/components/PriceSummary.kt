package dev.orderproof.emberkitchen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.orderproof.contract.QuoteResponse
import dev.orderproof.emberkitchen.core.formatMoney
import dev.orderproof.emberkitchen.ui.TestTags

/** Server-quoted subtotal, discount, tax and total. Each row reads as one phrase to screen readers. */
@Composable
fun PriceSummary(quote: QuoteResponse, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PriceRow("Subtotal", quote.subtotalMinor, TestTags.SUMMARY_SUBTOTAL)
        if (quote.discountMinor > 0) {
            PriceRow("Discount (${quote.coupon?.code.orEmpty()})", -quote.discountMinor, TestTags.SUMMARY_DISCOUNT)
        }
        PriceRow("Tax (8.25%)", quote.taxMinor, TestTags.SUMMARY_TAX)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        PriceRow("Total", quote.totalMinor, TestTags.SUMMARY_TOTAL, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun PriceRow(label: String, minor: Long, tag: String, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = style, modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp))
        Text(formatMoney(minor), style = style)
    }
}
