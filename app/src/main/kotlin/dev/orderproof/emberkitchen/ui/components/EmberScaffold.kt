package dev.orderproof.emberkitchen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.orderproof.emberkitchen.candidate.CandidateBuild
import dev.orderproof.emberkitchen.ui.TestTags
import dev.orderproof.emberkitchen.ui.theme.EmberColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmberScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, modifier = Modifier.semantics { heading() }, maxLines = 2) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = EmberColors.Charcoal,
                        titleContentColor = EmberColors.Cream,
                        navigationIconContentColor = EmberColors.Cream,
                        actionIconContentColor = EmberColors.Cream,
                    ),
                )
                if (CandidateBuild.IS_FAULTY) FaultyCandidateBanner()
            }
        },
        bottomBar = bottomBar,
        containerColor = EmberColors.Cream,
        content = content,
    )
}

/** Unmissable marker on every screen of the deliberately defective build. */
@Composable
private fun FaultyCandidateBanner() {
    Text(
        text = "FAULTY CANDIDATE · deliberate checkout defect · do not ship",
        color = EmberColors.Cream,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(EmberColors.Danger)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(TestTags.CANDIDATE_BANNER),
    )
}
