package com.easyprint.shop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easyprint.shop.printing.ColorMode
import com.easyprint.shop.printing.PaperSize
import com.easyprint.shop.printing.PrintDefaults
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSettingsSheet(
    defaults: PrintDefaults,
    onSelectDefaultPaper: (PaperSize) -> Unit,
    onSelectDefaultColor: (ColorMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.defaults_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.defaults_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.default_paper_size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsChoicePill(
                        label = stringResource(R.string.paper_a4),
                        selected = defaults.paperSize == PaperSize.A4,
                        onClick = { onSelectDefaultPaper(PaperSize.A4) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("default_paper_a4")
                    )
                    SettingsChoicePill(
                        label = stringResource(R.string.paper_a5),
                        selected = defaults.paperSize == PaperSize.A5,
                        onClick = { onSelectDefaultPaper(PaperSize.A5) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("default_paper_a5")
                    )
                    SettingsChoicePill(
                        label = stringResource(R.string.paper_legal),
                        selected = defaults.paperSize == PaperSize.LEGAL,
                        onClick = { onSelectDefaultPaper(PaperSize.LEGAL) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("default_paper_legal")
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.default_color_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsChoicePill(
                        label = stringResource(R.string.color_mode_color),
                        selected = defaults.colorMode == ColorMode.COLOR,
                        onClick = { onSelectDefaultColor(ColorMode.COLOR) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("default_color_color")
                    )
                    SettingsChoicePill(
                        label = stringResource(R.string.color_mode_bw),
                        selected = defaults.colorMode == ColorMode.BLACK_AND_WHITE,
                        onClick = { onSelectDefaultColor(ColorMode.BLACK_AND_WHITE) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("default_color_bw")
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("settings_done_button")
            ) {
                Text(
                    text = stringResource(R.string.done_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
