package com.easyprint.shop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyprint.shop.document.LoadedDocument
import com.easyprint.shop.printing.ColorMode
import com.easyprint.shop.printing.MultiPhotoLayout
import com.easyprint.shop.printing.PaperSize
import com.easyprint.shop.printing.PhotoPrintSize
import com.easyprint.shop.printing.PrintJobStatus
import com.easyprint.shop.printing.PrintPreset
import com.easyprint.shop.printing.SheetLayoutResult
import com.easyprint.shop.usb.UsbConnectionState
import com.example.R
import kotlin.math.min

@Composable
fun PreviewScreen(
    state: ScreenState.Preview,
    usbState: UsbConnectionState,
    printStatus: PrintJobStatus,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrintSettings: () -> Unit,
    onSelectPaperSize: (PaperSize) -> Unit,
    onSelectColorMode: (ColorMode) -> Unit,
    onSelectPhotoPrintSize: (PhotoPrintSize) -> Unit,
    onSelectMultiPhotoLayout: (MultiPhotoLayout) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrintStatusBanner(printStatus = printStatus)
                    Button(
                        onClick = onPrint,
                        enabled = printStatus !is PrintJobStatus.Preparing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("print_button"),
                        shape = MaterialTheme.shapes.medium,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Print,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.print_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Top App Header
            PreviewTopBar(
                documentName = state.document.displayName,
                onBack = onBack,
                onOpenSettings = onOpenSettings
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                val isWideLayout = maxWidth >= 680.dp

                if (isWideLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UsbStatusPill(usbState = usbState)
                            PaperPreviewArea(
                                state = state,
                                onPreviousPage = onPreviousPage,
                                onNextPage = onNextPage,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            PreviewControlsSection(
                                state = state,
                                usbState = usbState,
                                onOpenPrintSettings = onOpenPrintSettings,
                                onSelectPaperSize = onSelectPaperSize,
                                onSelectColorMode = onSelectColorMode,
                                onSelectPhotoPrintSize = onSelectPhotoPrintSize,
                                onSelectMultiPhotoLayout = onSelectMultiPhotoLayout
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UsbStatusPill(usbState = usbState)

                        PaperPreviewArea(
                            state = state,
                            onPreviousPage = onPreviousPage,
                            onNextPage = onNextPage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )

                        PreviewControlsSection(
                            state = state,
                            usbState = usbState,
                            onOpenPrintSettings = onOpenPrintSettings,
                            onSelectPaperSize = onSelectPaperSize,
                            onSelectColorMode = onSelectColorMode,
                            onSelectPhotoPrintSize = onSelectPhotoPrintSize,
                            onSelectMultiPhotoLayout = onSelectMultiPhotoLayout
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTopBar(
    documentName: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back_button),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = documentName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .testTag("preview_settings_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.settings_button),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun PaperPreviewArea(
    state: ScreenState.Preview,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val grayscaleFilter = remember(state.colorMode) {
        if (state.colorMode == ColorMode.BLACK_AND_WHITE) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else {
            null
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val availWidthPx = with(density) { (maxWidth - 16.dp).toPx().coerceAtLeast(80f) }
            val availHeightPx = with(density) { (maxHeight - 8.dp).toPx().coerceAtLeast(80f) }

            when (val doc = state.document) {
                is LoadedDocument.Pdf -> {
                    val isLandscape = PrintPreset.shouldUseLandscapeSheetForPdf(
                        firstPageWidth = doc.currentPageWidthPt,
                        firstPageHeight = doc.currentPageHeightPt
                    )
                    val sheetWmm = if (isLandscape) state.paperSize.heightMm else state.paperSize.widthMm
                    val sheetHmm = if (isLandscape) state.paperSize.widthMm else state.paperSize.heightMm
                    val pxPerMm = min(availWidthPx / sheetWmm, availHeightPx / sheetHmm)

                    val sheetWidthDp by animateDpAsState(
                        targetValue = with(density) { (sheetWmm * pxPerMm).toDp() },
                        animationSpec = tween(200),
                        label = "pdf_sheet_w"
                    )
                    val sheetHeightDp by animateDpAsState(
                        targetValue = with(density) { (sheetHmm * pxPerMm).toDp() },
                        animationSpec = tween(200),
                        label = "pdf_sheet_h"
                    )
                    val marginDp = with(density) { (PrintPreset.SAFE_MARGIN_MM * pxPerMm).toDp() }

                    PaperSheetSurface(
                        widthDp = sheetWidthDp,
                        heightDp = sheetHeightDp,
                        marginPx = PrintPreset.SAFE_MARGIN_MM * pxPerMm,
                        modifier = Modifier.testTag("paper_preview_sheet")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(marginDp),
                            contentAlignment = Alignment.Center
                        ) {
                            val imageBitmap = remember(doc.currentPageBitmap) {
                                doc.currentPageBitmap.asImageBitmap()
                            }
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = stringResource(
                                    R.string.pdf_page_preview_cd,
                                    doc.currentPageIndex + 1
                                ),
                                contentScale = ContentScale.Fit,
                                colorFilter = grayscaleFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (state.isPageLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }

                is LoadedDocument.Photos -> {
                    val layoutList: List<SheetLayoutResult> = remember(
                        doc,
                        state.paperSize,
                        state.photoPrintSize,
                        state.multiPhotoLayout
                    ) {
                        if (doc.items.size > 1 && state.multiPhotoLayout == MultiPhotoLayout.COMBINE_ON_SHEET) {
                            PrintPreset.computeCombinedSheetPages(
                                paperSize = state.paperSize,
                                imageDimensions = doc.items.map { it.orientedWidth to it.orientedHeight }
                            )
                        } else {
                            doc.items.map { photo ->
                                PrintPreset.computeSinglePhotoLayout(
                                    paperSize = state.paperSize,
                                    photoPrintSize = state.photoPrintSize,
                                    imageWidth = photo.orientedWidth,
                                    imageHeight = photo.orientedHeight
                                )
                            }
                        }
                    }

                    val safeSheetIdx = state.currentSheetIndex.coerceIn(0, (layoutList.size - 1).coerceAtLeast(0))
                    val activeLayout = layoutList.getOrNull(safeSheetIdx)

                    if (activeLayout != null) {
                        val pxPerMm = min(
                            availWidthPx / activeLayout.sheetWidthMm,
                            availHeightPx / activeLayout.sheetHeightMm
                        )
                        val sheetWidthDp by animateDpAsState(
                            targetValue = with(density) { (activeLayout.sheetWidthMm * pxPerMm).toDp() },
                            animationSpec = tween(200),
                            label = "photo_sheet_w"
                        )
                        val sheetHeightDp by animateDpAsState(
                            targetValue = with(density) { (activeLayout.sheetHeightMm * pxPerMm).toDp() },
                            animationSpec = tween(200),
                            label = "photo_sheet_h"
                        )

                        PaperSheetSurface(
                            widthDp = sheetWidthDp,
                            heightDp = sheetHeightDp,
                            marginPx = PrintPreset.SAFE_MARGIN_MM * pxPerMm,
                            modifier = Modifier.testTag("paper_preview_sheet")
                        ) {
                            val photosOnSheet = if (
                                doc.items.size > 1 && state.multiPhotoLayout == MultiPhotoLayout.COMBINE_ON_SHEET
                            ) {
                                val start = safeSheetIdx * PrintPreset.MAX_PHOTOS_PER_COMBINED_SHEET
                                doc.items.drop(start).take(PrintPreset.MAX_PHOTOS_PER_COMBINED_SHEET)
                            } else {
                                listOfNotNull(doc.items.getOrNull(safeSheetIdx))
                            }

                            val paperLabel = when (state.paperSize) {
                                PaperSize.A4 -> stringResource(R.string.paper_a4)
                                PaperSize.A5 -> stringResource(R.string.paper_a5)
                                PaperSize.LEGAL -> stringResource(R.string.paper_legal)
                            }

                            photosOnSheet.forEachIndexed { idx, photo ->
                                val rectMm = activeLayout.imageRectsMm.getOrNull(idx) ?: return@forEachIndexed
                                val leftDp by animateDpAsState(
                                    targetValue = with(density) { (rectMm.leftMm * pxPerMm).toDp() },
                                    animationSpec = tween(200),
                                    label = "photo_left_$idx"
                                )
                                val topDp by animateDpAsState(
                                    targetValue = with(density) { (rectMm.topMm * pxPerMm).toDp() },
                                    animationSpec = tween(200),
                                    label = "photo_top_$idx"
                                )
                                val wDp by animateDpAsState(
                                    targetValue = with(density) { (rectMm.widthMm * pxPerMm).toDp() },
                                    animationSpec = tween(200),
                                    label = "photo_w_$idx"
                                )
                                val hDp by animateDpAsState(
                                    targetValue = with(density) { (rectMm.heightMm * pxPerMm).toDp() },
                                    animationSpec = tween(200),
                                    label = "photo_h_$idx"
                                )
                                val photoBitmap = remember(photo.previewBitmap) {
                                    photo.previewBitmap.asImageBitmap()
                                }
                                Image(
                                    bitmap = photoBitmap,
                                    contentDescription = stringResource(R.string.photo_preview_cd, paperLabel),
                                    contentScale = ContentScale.Fit,
                                    colorFilter = grayscaleFilter,
                                    modifier = Modifier
                                        .offset(x = leftDp, y = topDp)
                                        .size(width = wDp, height = hDp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Page / Photo Stepper
        val totalItems = when (val doc = state.document) {
            is LoadedDocument.Pdf -> doc.pageCount
            is LoadedDocument.Photos -> {
                if (doc.items.size > 1 && state.multiPhotoLayout == MultiPhotoLayout.COMBINE_ON_SHEET) {
                    PrintPreset.computeCombinedSheetPages(
                        paperSize = state.paperSize,
                        imageDimensions = doc.items.map { it.orientedWidth to it.orientedHeight }
                    ).size
                } else {
                    doc.items.size
                }
            }
        }

        val currentIndex = when (val doc = state.document) {
            is LoadedDocument.Pdf -> doc.currentPageIndex
            is LoadedDocument.Photos -> state.currentSheetIndex
        }

        if (totalItems > 1) {
            val counterText = if (state.document is LoadedDocument.Photos &&
                state.multiPhotoLayout == MultiPhotoLayout.ONE_PER_SHEET
            ) {
                stringResource(R.string.photo_counter, currentIndex + 1, totalItems)
            } else {
                stringResource(R.string.page_counter, currentIndex + 1, totalItems)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPreviousPage,
                    enabled = currentIndex > 0 && !state.isPageLoading,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("prev_page_button"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.previous_page)
                    )
                    Text(
                        text = stringResource(R.string.previous_page),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text(
                    text = counterText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                OutlinedButton(
                    onClick = onNextPage,
                    enabled = currentIndex + 1 < totalItems && !state.isPageLoading,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("next_page_button"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.next_page),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.next_page)
                    )
                }
            }
        } else if (state.document is LoadedDocument.Pdf) {
            Text(
                text = stringResource(R.string.page_counter, 1, 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.document is LoadedDocument.Photos) {
            Text(
                text = stringResource(R.string.borderless_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PaperSheetSurface(
    widthDp: androidx.compose.ui.unit.Dp,
    heightDp: androidx.compose.ui.unit.Dp,
    marginPx: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val marginGuideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    Surface(
        modifier = modifier.size(width = widthDp, height = heightDp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val inset = marginPx.coerceIn(2f, min(size.width, size.height) / 4f)
                drawRect(
                    color = marginGuideColor,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2f * inset, size.height - 2f * inset),
                    style = Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                )
            }
            content()
        }
    }
}

@Composable
private fun PreviewControlsSection(
    state: ScreenState.Preview,
    usbState: UsbConnectionState,
    onOpenPrintSettings: () -> Unit,
    onSelectPaperSize: (PaperSize) -> Unit,
    onSelectColorMode: (ColorMode) -> Unit,
    onSelectPhotoPrintSize: (PhotoPrintSize) -> Unit,
    onSelectMultiPhotoLayout: (MultiPhotoLayout) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.document is LoadedDocument.Photos) {
            if (state.document.items.size > 1) {
                MultiPhotoLayoutSelector(
                    selectedLayout = state.multiPhotoLayout,
                    onSelectLayout = onSelectMultiPhotoLayout
                )
            }
            if (state.document.items.size == 1 ||
                state.multiPhotoLayout == MultiPhotoLayout.ONE_PER_SHEET
            ) {
                PhotoSizeSelector(
                    selectedSize = state.photoPrintSize,
                    onSelectSize = onSelectPhotoPrintSize
                )
            }
        }

        // Paper Size Quick Pills
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.paper_size_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ControlChoicePill(
                    label = stringResource(R.string.paper_a4),
                    selected = state.paperSize == PaperSize.A4,
                    onClick = { onSelectPaperSize(PaperSize.A4) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("paper_a4")
                )
                ControlChoicePill(
                    label = stringResource(R.string.paper_a5),
                    selected = state.paperSize == PaperSize.A5,
                    onClick = { onSelectPaperSize(PaperSize.A5) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("paper_a5")
                )
                ControlChoicePill(
                    label = stringResource(R.string.paper_legal),
                    selected = state.paperSize == PaperSize.LEGAL,
                    onClick = { onSelectPaperSize(PaperSize.LEGAL) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("paper_legal")
                )
            }
        }

        // Colour / Black & White Quick Pills
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.color_mode_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ControlChoicePill(
                    label = stringResource(R.string.color_mode_color),
                    selected = state.colorMode == ColorMode.COLOR,
                    onClick = { onSelectColorMode(ColorMode.COLOR) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("color_mode_color")
                )
                ControlChoicePill(
                    label = stringResource(R.string.color_mode_bw),
                    selected = state.colorMode == ColorMode.BLACK_AND_WHITE,
                    onClick = { onSelectColorMode(ColorMode.BLACK_AND_WHITE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("color_mode_bw")
                )
            }
        }

        PrintServiceHelperCard(
            usbState = usbState,
            onOpenPrintSettings = onOpenPrintSettings
        )
    }
}

@Composable
private fun ControlChoicePill(
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
