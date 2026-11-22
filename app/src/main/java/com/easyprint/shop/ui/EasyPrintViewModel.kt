package com.easyprint.shop.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.easyprint.shop.data.PreferencesRepository
import com.easyprint.shop.document.DocumentError
import com.easyprint.shop.document.DocumentLoadOutcome
import com.easyprint.shop.document.DocumentRepository
import com.easyprint.shop.document.LoadedDocument
import com.easyprint.shop.document.PdfPageRenderOutcome
import com.easyprint.shop.printing.ColorMode
import com.easyprint.shop.printing.MultiPhotoLayout
import com.easyprint.shop.printing.PaperSize
import com.easyprint.shop.printing.PhotoPrintSize
import com.easyprint.shop.printing.PrintCoordinator
import com.easyprint.shop.printing.PrintDefaults
import com.easyprint.shop.printing.PrintJobStatus
import com.easyprint.shop.printing.PrintPreset
import com.easyprint.shop.usb.UsbConnectionState
import com.easyprint.shop.usb.UsbMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScreenState {
    data object Home : ScreenState
    data object Loading : ScreenState
    data class Preview(
        val document: LoadedDocument,
        val paperSize: PaperSize,
        val colorMode: ColorMode,
        val photoPrintSize: PhotoPrintSize = PhotoPrintSize.MEDIUM,
        val multiPhotoLayout: MultiPhotoLayout = MultiPhotoLayout.ONE_PER_SHEET,
        val currentSheetIndex: Int = 0,
        val isPageLoading: Boolean = false
    ) : ScreenState

    data class Error(val error: DocumentError) : ScreenState
}

class EasyPrintViewModel(
    private val documentRepository: DocumentRepository,
    private val preferencesRepository: PreferencesRepository,
    private val usbMonitor: UsbMonitor,
    private val printCoordinator: PrintCoordinator
) : ViewModel() {

    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Home)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    val usbState: StateFlow<UsbConnectionState> = usbMonitor.state

    val defaults: StateFlow<PrintDefaults> = preferencesRepository.defaultsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PrintDefaults()
        )

    private val _printStatus = MutableStateFlow<PrintJobStatus>(PrintJobStatus.Idle)
    val printStatus: StateFlow<PrintJobStatus> = _printStatus.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    // Tracks whether user explicitly saved a preferred color mode in Settings so PDFs can default to B&W initially.
    private var hasExplicitColorOverride = false
    private var loadJob: Job? = null
    private var pageJob: Job? = null

    init {
        // Clean up any stale temporary cache files from a prior interrupted session.
        documentRepository.clearTemporaryFiles()
    }

    fun handleIncomingIntent(intent: Intent?): Boolean {
        val uris = documentRepository.extractUrisFromIntent(intent)
        if (uris.isEmpty()) return false
        loadUris(uris)
        return true
    }

    fun loadUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        loadJob?.cancel()
        pageJob?.cancel()
        printCoordinator.cancelObservation()
        _isSettingsOpen.value = false
        _printStatus.value = PrintJobStatus.Idle
        recycleCurrentBitmaps()
        _screenState.value = ScreenState.Loading

        loadJob = viewModelScope.launch {
            val savedDefaults = runCatching { preferencesRepository.defaultsFlow.first() }
                .getOrDefault(PrintDefaults())

            when (val outcome = documentRepository.loadFromUris(uris)) {
                is DocumentLoadOutcome.Failure -> {
                    _screenState.value = ScreenState.Error(outcome.error)
                }
                is DocumentLoadOutcome.Success -> {
                    val doc = outcome.document
                    val initialColorMode = when {
                        hasExplicitColorOverride -> savedDefaults.colorMode
                        doc is LoadedDocument.Pdf -> ColorMode.BLACK_AND_WHITE
                        else -> savedDefaults.colorMode
                    }
                    _screenState.value = ScreenState.Preview(
                        document = doc,
                        paperSize = savedDefaults.paperSize,
                        colorMode = initialColorMode,
                        photoPrintSize = PhotoPrintSize.MEDIUM,
                        multiPhotoLayout = MultiPhotoLayout.ONE_PER_SHEET,
                        currentSheetIndex = 0
                    )
                }
            }
        }
    }

    fun selectPaperSize(paperSize: PaperSize) {
        val current = _screenState.value as? ScreenState.Preview ?: return
        _printStatus.value = PrintJobStatus.Idle
        _screenState.value = current.copy(paperSize = paperSize)
    }

    fun selectColorMode(colorMode: ColorMode) {
        val current = _screenState.value as? ScreenState.Preview ?: return
        _printStatus.value = PrintJobStatus.Idle
        _screenState.value = current.copy(colorMode = colorMode)
    }

    fun selectPhotoPrintSize(photoPrintSize: PhotoPrintSize) {
        val current = _screenState.value as? ScreenState.Preview ?: return
        _printStatus.value = PrintJobStatus.Idle
        _screenState.value = current.copy(photoPrintSize = photoPrintSize)
    }

    fun selectMultiPhotoLayout(layout: MultiPhotoLayout) {
        val current = _screenState.value as? ScreenState.Preview ?: return
        _printStatus.value = PrintJobStatus.Idle
        _screenState.value = current.copy(
            multiPhotoLayout = layout,
            currentSheetIndex = 0
        )
    }

    fun goToPreviousPageOrSheet() {
        val current = _screenState.value as? ScreenState.Preview ?: return
        when (val doc = current.document) {
            is LoadedDocument.Pdf -> {
                if (doc.currentPageIndex > 0 && !current.isPageLoading) {
                    loadPdfPage(current, doc, doc.currentPageIndex - 1)
                }
            }
            is LoadedDocument.Photos -> {
                if (current.currentSheetIndex > 0) {
                    _screenState.value = current.copy(currentSheetIndex = current.currentSheetIndex - 1)
                }
            }
        }
    }

    fun goToNextPageOrSheet() {
        val current = _screenState.value as? ScreenState.Preview ?: return
        when (val doc = current.document) {
            is LoadedDocument.Pdf -> {
                if (doc.currentPageIndex + 1 < doc.pageCount && !current.isPageLoading) {
                    loadPdfPage(current, doc, doc.currentPageIndex + 1)
                }
            }
            is LoadedDocument.Photos -> {
                val maxSheets = totalPhotoSheets(doc, current.paperSize, current.multiPhotoLayout)
                if (current.currentSheetIndex + 1 < maxSheets) {
                    _screenState.value = current.copy(currentSheetIndex = current.currentSheetIndex + 1)
                }
            }
        }
    }

    private fun totalPhotoSheets(
        doc: LoadedDocument.Photos,
        paperSize: PaperSize,
        layout: MultiPhotoLayout
    ): Int {
        return if (doc.items.size > 1 && layout == MultiPhotoLayout.COMBINE_ON_SHEET) {
            PrintPreset.computeCombinedSheetPages(
                paperSize = paperSize,
                imageDimensions = doc.items.map { it.orientedWidth to it.orientedHeight }
            ).size
        } else {
            doc.items.size
        }
    }

    private fun loadPdfPage(
        current: ScreenState.Preview,
        doc: LoadedDocument.Pdf,
        targetIndex: Int
    ) {
        pageJob?.cancel()
        _screenState.value = current.copy(isPageLoading = true)
        pageJob = viewModelScope.launch {
            when (val outcome = documentRepository.renderPdfPage(doc, targetIndex)) {
                is PdfPageRenderOutcome.Success -> {
                    val latest = _screenState.value as? ScreenState.Preview ?: return@launch
                    val oldBitmap = (latest.document as? LoadedDocument.Pdf)?.currentPageBitmap
                    val updatedDoc = doc.copy(
                        currentPageIndex = targetIndex,
                        currentPageWidthPt = outcome.pageWidthPt,
                        currentPageHeightPt = outcome.pageHeightPt,
                        currentPageBitmap = outcome.bitmap
                    )
                    _screenState.value = latest.copy(
                        document = updatedDoc,
                        isPageLoading = false
                    )
                    if (oldBitmap != null && oldBitmap !== outcome.bitmap) {
                        oldBitmap.recycle()
                    }
                }
                is PdfPageRenderOutcome.Failure -> {
                    _screenState.value = ScreenState.Error(outcome.error)
                }
            }
        }
    }

    fun startPrint(activity: Activity) {
        val current = _screenState.value as? ScreenState.Preview ?: return
        usbMonitor.refresh()
        printCoordinator.startPrint(
            activity = activity,
            document = current.document,
            paperSize = current.paperSize,
            colorMode = current.colorMode,
            photoPrintSize = current.photoPrintSize,
            multiPhotoLayout = current.multiPhotoLayout,
            scope = viewModelScope,
            onStatusChanged = { status ->
                _printStatus.value = status
            }
        )
    }

    fun returnHomeAndCleanup() {
        loadJob?.cancel()
        pageJob?.cancel()
        printCoordinator.cancelObservation()
        recycleCurrentBitmaps()
        documentRepository.clearTemporaryFiles()
        _printStatus.value = PrintJobStatus.Idle
        _screenState.value = ScreenState.Home
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun updateDefaultPaperSize(paperSize: PaperSize) {
        viewModelScope.launch {
            preferencesRepository.updateDefaultPaperSize(paperSize)
        }
        val current = _screenState.value as? ScreenState.Preview
        if (current != null) {
            _screenState.value = current.copy(paperSize = paperSize)
        }
    }

    fun updateDefaultColorMode(colorMode: ColorMode) {
        hasExplicitColorOverride = true
        viewModelScope.launch {
            preferencesRepository.updateDefaultColorMode(colorMode)
        }
        val current = _screenState.value as? ScreenState.Preview
        if (current != null) {
            _screenState.value = current.copy(colorMode = colorMode)
        }
    }

    fun refreshUsbStatus() {
        usbMonitor.refresh()
    }

    private fun recycleCurrentBitmaps() {
        val current = (_screenState.value as? ScreenState.Preview)?.document ?: return
        when (current) {
            is LoadedDocument.Pdf -> runCatching { current.currentPageBitmap.recycle() }
            is LoadedDocument.Photos -> current.items.forEach {
                runCatching { it.previewBitmap.recycle() }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        printCoordinator.cancelObservation()
        recycleCurrentBitmaps()
        documentRepository.clearTemporaryFiles()
    }

    class Factory(
        private val documentRepository: DocumentRepository,
        private val preferencesRepository: PreferencesRepository,
        private val usbMonitor: UsbMonitor,
        private val printCoordinator: PrintCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EasyPrintViewModel(
                documentRepository = documentRepository,
                preferencesRepository = preferencesRepository,
                usbMonitor = usbMonitor,
                printCoordinator = printCoordinator
            ) as T
        }
    }
}
