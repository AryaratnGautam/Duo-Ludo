package com.easyprint.shop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyprint.shop.printing.MultiPhotoLayout
import com.easyprint.shop.printing.PhotoPrintSize
import com.example.R

@Composable
fun PhotoSizeSelector(
    selectedSize: PhotoPrintSize,
    onSelectSize: (PhotoPrintSize) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.photo_size_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PhotoSizeOptionButton(
                label = stringResource(R.string.photo_size_small),
                fillFraction = 0.44f,
                selected = selectedSize == PhotoPrintSize.SMALL,
                onClick = { onSelectSize(PhotoPrintSize.SMALL) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("photo_size_small")
            )
            PhotoSizeOptionButton(
                label = stringResource(R.string.photo_size_medium),
                fillFraction = 0.58f,
                selected = selectedSize == PhotoPrintSize.MEDIUM,
                onClick = { onSelectSize(PhotoPrintSize.MEDIUM) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("photo_size_medium")
            )
            PhotoSizeOptionButton(
                label = stringResource(R.string.photo_size_large),
                fillFraction = 0.72f,
                selected = selectedSize == PhotoPrintSize.LARGE,
                onClick = { onSelectSize(PhotoPrintSize.LARGE) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("photo_size_large")
            )
            PhotoSizeOptionButton(
                label = stringResource(R.string.photo_size_full_sheet),
                fillFraction = 0.88f,
                selected = selectedSize == PhotoPrintSize.FULL_SHEET,
                onClick = { onSelectSize(PhotoPrintSize.FULL_SHEET) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("photo_size_full_sheet")
            )
        }
    }
}

@Composable
private fun PhotoSizeOptionButton(
    label: String,
    fillFraction: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 64.dp),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val paperStroke = contentColor.copy(alpha = 0.55f)
            val fillTint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            }
            Canvas(modifier = Modifier.size(width = 20.dp, height = 26.dp)) {
                drawRoundRect(
                    color = paperStroke,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(3f, 3f),
                    style = Stroke(width = 2f)
                )
                val innerW = size.width * fillFraction
                val innerH = size.height * fillFraction
                drawRoundRect(
                    color = fillTint,
                    topLeft = Offset((size.width - innerW) / 2f, (size.height - innerH) / 2f),
                    size = Size(innerW, innerH),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MultiPhotoLayoutSelector(
    selectedLayout: MultiPhotoLayout,
    onSelectLayout: (MultiPhotoLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.multi_photo_layout_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LayoutChoicePill(
                label = stringResource(R.string.layout_one_per_sheet),
                selected = selectedLayout == MultiPhotoLayout.ONE_PER_SHEET,
                onClick = { onSelectLayout(MultiPhotoLayout.ONE_PER_SHEET) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("layout_one_per_sheet")
            )
            LayoutChoicePill(
                label = stringResource(R.string.layout_combine_on_sheet),
                selected = selectedLayout == MultiPhotoLayout.COMBINE_ON_SHEET,
                onClick = { onSelectLayout(MultiPhotoLayout.COMBINE_ON_SHEET) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("layout_combine_on_sheet")
            )
        }
    }
}

@Composable
private fun LayoutChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
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
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
