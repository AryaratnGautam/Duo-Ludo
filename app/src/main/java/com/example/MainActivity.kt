package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easyprint.shop.data.PreferencesRepository
import com.easyprint.shop.document.DocumentRepository
import com.easyprint.shop.document.ImageProcessor
import com.easyprint.shop.document.IncomingFileHandler
import com.easyprint.shop.document.PdfProcessor
import com.easyprint.shop.printing.PrintCoordinator
import com.easyprint.shop.ui.EasyPrintErrorView
import com.easyprint.shop.ui.EasyPrintLoadingView
import com.easyprint.shop.ui.EasyPrintViewModel
import com.easyprint.shop.ui.HomeScreen
import com.easyprint.shop.ui.PreviewScreen
import com.easyprint.shop.ui.PrintSettingsSheet
import com.easyprint.shop.ui.ScreenState
import com.easyprint.shop.ui.theme.EasyPrintTheme
import com.easyprint.shop.usb.UsbMonitor

class MainActivity : ComponentActivity() {

    private lateinit var usbMonitor: UsbMonitor
    private lateinit var incomingFileHandler: IncomingFileHandler

    private val viewModel: EasyPrintViewModel by viewModels {
        val appContext = applicationContext
        val imageProcessor = ImageProcessor()
        val documentRepository = DocumentRepository(
            incomingFileHandler = incomingFileHandler,
            pdfProcessor = PdfProcessor(),
            imageProcessor = imageProcessor
        )
        EasyPrintViewModel.Factory(
            documentRepository = documentRepository,
            preferencesRepository = PreferencesRepository(appContext),
            usbMonitor = usbMonitor,
            printCoordinator = PrintCoordinator(imageProcessor)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        usbMonitor = UsbMonitor(applicationContext)
        incomingFileHandler = IncomingFileHandler(applicationContext)

        if (savedInstanceState == null) {
            viewModel.handleIncomingIntent(intent)
        }

        setContent {
            EasyPrintTheme {
                EasyPrintApp(
                    viewModel = viewModel,
                    onPrintClick = {
                        viewModel.startPrint(this@MainActivity)
                    },
                    onOpenPrintSettings = {
                        runCatching {
                            startActivity(UsbMonitor.createOpenPrintSettingsIntent())
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        usbMonitor.startMonitoring()
    }

    override fun onStop() {
        super.onStop()
        usbMonitor.stopMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            incomingFileHandler.clearTempFiles()
        }
    }
}

@Composable
fun EasyPrintApp(
    viewModel: EasyPrintViewModel,
    onPrintClick: () -> Unit,
    onOpenPrintSettings: () -> Unit
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val usbState by viewModel.usbState.collectAsStateWithLifecycle()
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()
    val printStatus by viewModel.printStatus.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadUris(listOf(uri))
        }
    }

    val launchFilePicker = {
        openDocumentLauncher.launch(
            arrayOf(
                "application/pdf",
                "application/x-pdf",
                "image/jpeg",
                "image/png"
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val current = screenState) {
            is ScreenState.Home -> {
                HomeScreen(
                    usbState = usbState,
                    onSelectFile = launchFilePicker,
                    onOpenSettings = { viewModel.openSettings() },
                    onOpenPrintSettings = onOpenPrintSettings
                )
            }

            is ScreenState.Loading -> {
                EasyPrintLoadingView(modifier = Modifier.statusBarsPadding())
            }

            is ScreenState.Preview -> {
                PreviewScreen(
                    state = current,
                    usbState = usbState,
                    printStatus = printStatus,
                    onBack = { viewModel.returnHomeAndCleanup() },
                    onOpenSettings = { viewModel.openSettings() },
                    onOpenPrintSettings = onOpenPrintSettings,
                    onSelectPaperSize = { viewModel.selectPaperSize(it) },
                    onSelectColorMode = { viewModel.selectColorMode(it) },
                    onSelectPhotoPrintSize = { viewModel.selectPhotoPrintSize(it) },
                    onSelectMultiPhotoLayout = { viewModel.selectMultiPhotoLayout(it) },
                    onPreviousPage = { viewModel.goToPreviousPageOrSheet() },
                    onNextPage = { viewModel.goToNextPageOrSheet() },
                    onPrint = onPrintClick
                )
            }

            is ScreenState.Error -> {
                BackHandler { viewModel.returnHomeAndCleanup() }
                EasyPrintErrorView(
                    error = current.error,
                    onSelectAnotherFile = launchFilePicker,
                    onBackHome = { viewModel.returnHomeAndCleanup() },
                    modifier = Modifier.statusBarsPadding()
                )
            }
        }

        if (isSettingsOpen) {
            PrintSettingsSheet(
                defaults = defaults,
                onSelectDefaultPaper = { viewModel.updateDefaultPaperSize(it) },
                onSelectDefaultColor = { viewModel.updateDefaultColorMode(it) },
                onDismiss = { viewModel.closeSettings() }
            )
        }
    }
}
