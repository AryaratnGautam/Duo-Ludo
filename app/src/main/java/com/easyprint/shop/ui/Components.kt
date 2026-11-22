package com.easyprint.shop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PrintDisabled
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyprint.shop.document.DocumentError
import com.easyprint.shop.printing.PrintJobStatus
import com.easyprint.shop.ui.theme.StatusAmberContainer
import com.easyprint.shop.ui.theme.StatusAmberText
import com.easyprint.shop.ui.theme.StatusGreenContainer
import com.easyprint.shop.ui.theme.StatusGreenText
import com.easyprint.shop.ui.theme.StatusRedContainer
import com.easyprint.shop.ui.theme.StatusRedText
import com.easyprint.shop.usb.PrintServiceStatus
import com.easyprint.shop.usb.UsbConnectionState
import com.example.R

@Composable
fun UsbStatusPill(
    usbState: UsbConnectionState,
    modifier: Modifier = Modifier
) {
    val (bgColor, fgColor, icon, label) = when {
        usbState.isChecking -> Quad(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Rounded.Usb,
            stringResource(R.string.status_checking_printer)
        )
        !usbState.isUsbHostSupported -> Quad(
            StatusRedContainer,
            StatusRedText,
            Icons.Rounded.UsbOff,
            stringResource(R.string.status_otg_unavailable)
        )
        usbState.isUsbDeviceDetected -> {
            val deviceHint = usbState.attachedDeviceNames.firstOrNull()
            val text = if (!deviceHint.isNullOrBlank()) {
                "${stringResource(R.string.status_usb_connected)} · $deviceHint"
            } else {
                stringResource(R.string.status_usb_connected)
            }
            Quad(
                StatusGreenContainer,
                StatusGreenText,
                Icons.Rounded.Usb,
                text
            )
        }
        else -> Quad(
            StatusAmberContainer,
            StatusAmberText,
            Icons.Rounded.UsbOff,
            stringResource(R.string.status_connect_otg)
        )
    }

    Surface(
        modifier = modifier.testTag("usb_status_pill"),
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fgColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fgColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PrintServiceHelperCard(
    usbState: UsbConnectionState,
    onOpenPrintSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showHelper = usbState.printServiceStatus == PrintServiceStatus.HP_PLUGIN_MISSING_OR_DISABLED ||
        usbState.printServiceStatus == PrintServiceStatus.NO_USABLE_PRINT_SERVICE

    if (!showHelper) return

    val message = if (usbState.printServiceStatus == PrintServiceStatus.NO_USABLE_PRINT_SERVICE) {
        stringResource(R.string.status_no_usable_print_service)
    } else {
        stringResource(R.string.status_hp_plugin_missing)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("print_service_helper_card"),
        color = StatusAmberContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, StatusAmberText.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PrintDisabled,
                    contentDescription = null,
                    tint = StatusAmberText,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.status_print_service_setup),
                    style = MaterialTheme.typography.titleMedium,
                    color = StatusAmberText
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(
                onClick = onOpenPrintSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_print_settings_button"),
                border = BorderStroke(1.5.dp, StatusAmberText),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAmberText)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.open_print_settings),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun PrintStatusBanner(
    printStatus: PrintJobStatus,
    modifier: Modifier = Modifier
) {
    if (printStatus is PrintJobStatus.Idle) return

    val (bg, fg, icon, text) = when (printStatus) {
        PrintJobStatus.Idle -> return
        PrintJobStatus.Preparing -> Quad(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.Print,
            stringResource(R.string.preparing_document)
        )
        PrintJobStatus.DialogOpened -> Quad(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Rounded.Info,
            stringResource(R.string.print_dialog_opened)
        )
        PrintJobStatus.CompletedSent -> Quad(
            StatusGreenContainer,
            StatusGreenText,
            Icons.Rounded.CheckCircle,
            stringResource(R.string.print_completed_sent)
        )
        PrintJobStatus.Cancelled -> Quad(
            StatusAmberContainer,
            StatusAmberText,
            Icons.Rounded.WarningAmber,
            stringResource(R.string.print_cancelled)
        )
        PrintJobStatus.Failed -> Quad(
            StatusRedContainer,
            StatusRedText,
            Icons.Rounded.ErrorOutline,
            stringResource(R.string.print_failed)
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("print_status_banner"),
        color = bg,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = fg
            )
        }
    }
}

@Composable
fun EasyPrintLoadingView(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                modifier = Modifier.size(52.dp)
            )
            Text(
                text = stringResource(R.string.preparing_document),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EasyPrintErrorView(
    error: DocumentError,
    onSelectAnotherFile: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = when (error) {
        DocumentError.UnsupportedFile -> stringResource(R.string.error_unsupported_file)
        DocumentError.CorruptedPdf -> stringResource(R.string.error_corrupted_pdf)
        DocumentError.PasswordProtectedPdf -> stringResource(R.string.error_password_pdf)
        DocumentError.UriPermissionFailure -> stringResource(R.string.error_uri_permission)
        DocumentError.InsufficientStorage -> stringResource(R.string.error_insufficient_storage)
        DocumentError.VeryLargeImage -> stringResource(R.string.error_large_image)
        DocumentError.GenericReadFailure -> stringResource(R.string.error_generic)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .testTag("error_card"),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = StatusRedContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = StatusRedText,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(40.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.error_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onSelectAnotherFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("error_select_another_button")
                ) {
                    Text(
                        text = stringResource(R.string.try_another_file),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                OutlinedButton(
                    onClick = onBackHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("error_back_home_button")
                ) {
                    Text(
                        text = stringResource(R.string.back_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
