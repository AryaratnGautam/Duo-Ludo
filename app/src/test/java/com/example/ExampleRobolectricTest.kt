package com.example

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.easyprint.shop.data.PreferencesRepository
import com.easyprint.shop.document.DocumentError
import com.easyprint.shop.document.DocumentLoadOutcome
import com.easyprint.shop.document.DocumentRepository
import com.easyprint.shop.document.ImageProcessor
import com.easyprint.shop.document.IncomingFileHandler
import com.easyprint.shop.document.LoadedDocument
import com.easyprint.shop.document.PdfProcessor
import com.easyprint.shop.document.PhotoEntry
import com.easyprint.shop.printing.ColorMode
import com.easyprint.shop.printing.MultiPhotoLayout
import com.easyprint.shop.printing.PaperSize
import com.easyprint.shop.printing.PhotoPrintSize
import com.easyprint.shop.printing.PrintCoordinator
import com.easyprint.shop.printing.PrintJobStatus
import com.easyprint.shop.ui.EasyPrintViewModel
import com.easyprint.shop.ui.HomeScreen
import com.easyprint.shop.ui.PreviewScreen
import com.easyprint.shop.ui.ScreenState
import com.easyprint.shop.ui.theme.EasyPrintTheme
import com.easyprint.shop.usb.UsbConnectionState
import com.easyprint.shop.usb.UsbMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read English and Hindi app strings from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("EasyPrint", context.getString(R.string.app_name))
        assertEquals("SELECT FILE", context.getString(R.string.select_file))

        val hiConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("hi"))
        }
        val hiContext = context.createConfigurationContext(hiConfig)
        assertEquals("फ़ाइल चुनें", hiContext.getString(R.string.select_file))
        assertEquals("प्रिंट करें", hiContext.getString(R.string.print_button))
    }

    @Test
    fun `incoming file handler extracts VIEW SEND and SEND_MULTIPLE uris`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val handler = IncomingFileHandler(context)

        val uri1 = Uri.parse("content://com.whatsapp.provider.media/item/1")
        val uri2 = Uri.parse("content://com.whatsapp.provider.media/item/2")

        val viewIntent = Intent(Intent.ACTION_VIEW).setData(uri1)
        assertEquals(listOf(uri1), handler.extractUrisFromIntent(viewIntent))

        val sendIntent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri1)
        assertEquals(listOf(uri1), handler.extractUrisFromIntent(sendIntent))

        val sendMultipleIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri1, uri2))
            clipData = ClipData.newRawUri("extra", uri2)
        }
        assertEquals(listOf(uri1, uri2), handler.extractUrisFromIntent(sendMultipleIntent))
    }

    @Test
    fun `repository rejects unsupported files and cleans up temp directory`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val handler = IncomingFileHandler(context)
            val tempInput = File(context.cacheDir, "bad_input.txt").apply {
                writeText("not a pdf or image")
            }

            val repo = DocumentRepository(handler, PdfProcessor(), ImageProcessor())
            val outcome = repo.loadFromUris(listOf(Uri.fromFile(tempInput)))

            assertTrue(outcome is DocumentLoadOutcome.Failure)
            assertEquals(DocumentError.UnsupportedFile, (outcome as DocumentLoadOutcome.Failure).error)

            val tempDir = File(context.cacheDir, "easyprint_temp")
            assertTrue(!tempDir.exists() || tempDir.listFiles().isNullOrEmpty())
            tempInput.delete()
        }
    }

    @Test
    fun `incoming handler detects extensionless pdf by header and cleans up temp cache`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val handler = IncomingFileHandler(context)
            val pdfInput = File(context.cacheDir, "WA_DOCUMENT_NO_EXT").apply {
                writeBytes("%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".toByteArray())
            }

            val copyOutcome = handler.copyToPrivateCache(listOf(Uri.fromFile(pdfInput)))
            assertTrue(copyOutcome is com.easyprint.shop.document.IncomingCopyOutcome.Success)
            val copied = (copyOutcome as com.easyprint.shop.document.IncomingCopyOutcome.Success).files.first()
            assertEquals(com.easyprint.shop.document.DetectedFileType.PDF, copied.fileType)
            assertTrue(copied.file.name.endsWith(".pdf"))

            // Verify PdfProcessor returns a clean Failure when a file cannot be opened.
            val missingPdf = File(context.cacheDir, "missing_file.pdf")
            val inspectMissing = PdfProcessor().inspectPdf(missingPdf)
            assertTrue(inspectMissing is com.easyprint.shop.document.PdfInspectOutcome.Failure)
            assertEquals(
                DocumentError.CorruptedPdf,
                (inspectMissing as com.easyprint.shop.document.PdfInspectOutcome.Failure).error
            )

            handler.clearTempFiles()
            val tempDir = File(context.cacheDir, "easyprint_temp")
            assertTrue(!tempDir.exists() || tempDir.listFiles().isNullOrEmpty())
            pdfInput.delete()
        }
    }

    @Test
    fun `repository loads multiple photos and respects EXIF orientation`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val img1 = File(context.cacheDir, "photo_one.jpg")
            val img2 = File(context.cacheDir, "photo_two.png")

            val bmp1 = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
            FileOutputStream(img1).use { bmp1.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp1.recycle()
            ExifInterface(img1.absolutePath).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_ROTATE_90.toString()
                )
                saveAttributes()
            }

            val bmp2 = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
            FileOutputStream(img2).use { bmp2.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp2.recycle()

            val repo = DocumentRepository(
                IncomingFileHandler(context),
                PdfProcessor(),
                ImageProcessor()
            )
            val outcome = repo.loadFromUris(listOf(Uri.fromFile(img1), Uri.fromFile(img2)))
            assertTrue(outcome is DocumentLoadOutcome.Success)

            val photos = (outcome as DocumentLoadOutcome.Success).document as LoadedDocument.Photos
            assertEquals(2, photos.items.size)
            // First photo was 200x100 rotated 90 degrees via EXIF -> 100x200
            assertEquals(100, photos.items[0].orientedWidth)
            assertEquals(200, photos.items[0].orientedHeight)
            assertEquals("photo_one.jpg (+1)", photos.displayName)

            repo.clearTemporaryFiles()
            img1.delete()
            img2.delete()
        }
    }

    @Test
    fun `preferences repository persists default paper size and color mode`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefsRepo = PreferencesRepository(context)
            prefsRepo.updateDefaultPaperSize(PaperSize.LEGAL)
            prefsRepo.updateDefaultColorMode(ColorMode.BLACK_AND_WHITE)

            val loaded = prefsRepo.defaultsFlow.first()
            assertEquals(PaperSize.LEGAL, loaded.paperSize)
            assertEquals(ColorMode.BLACK_AND_WHITE, loaded.colorMode)
        }
    }

    @Test
    fun `view model handles settings modal and cleanup on return home`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imageProcessor = ImageProcessor()
        val repo = DocumentRepository(IncomingFileHandler(context), PdfProcessor(), imageProcessor)
        val vm = EasyPrintViewModel(
            documentRepository = repo,
            preferencesRepository = PreferencesRepository(context),
            usbMonitor = UsbMonitor(context),
            printCoordinator = PrintCoordinator(imageProcessor)
        )

        assertTrue(vm.screenState.value is ScreenState.Home)
        vm.openSettings()
        assertTrue(vm.isSettingsOpen.value)
        vm.closeSettings()
        assertFalse(vm.isSettingsOpen.value)

        vm.returnHomeAndCleanup()
        assertTrue(vm.screenState.value is ScreenState.Home)
    }

    @Test
    fun `usb monitor refreshes cleanly without claiming usb ownership`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = UsbMonitor(context)
        monitor.refresh()
        val state = monitor.state.value
        assertFalse(state.isChecking)
        assertEquals(0, state.attachedDeviceCount)
    }

    @Test
    fun `home screen renders select file button and usb pill`() {
        var selectClicked = false
        composeTestRule.setContent {
            EasyPrintTheme {
                HomeScreen(
                    usbState = UsbConnectionState(isChecking = false, isUsbHostSupported = true),
                    onSelectFile = { selectClicked = true },
                    onOpenSettings = {},
                    onOpenPrintSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("usb_status_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("select_file_button").assertIsDisplayed().performClick()
        assertTrue(selectClicked)
    }

    @Test
    fun `preview screen allows switching photo sizes paper sizes color mode and printing`() {
        val bitmap = Bitmap.createBitmap(100, 150, Bitmap.Config.ARGB_8888)
        val photo = PhotoEntry(
            file = File("dummy.jpg"),
            displayName = "IMG_2026.jpg",
            previewBitmap = bitmap,
            orientedWidth = 1000,
            orientedHeight = 1500,
            exifOrientation = 1
        )
        var selectedSize = PhotoPrintSize.MEDIUM
        var selectedPaper = PaperSize.A4
        var selectedColor = ColorMode.COLOR
        var printPressed = false

        composeTestRule.setContent {
            EasyPrintTheme {
                PreviewScreen(
                    state = ScreenState.Preview(
                        document = LoadedDocument.Photos(
                            items = listOf(photo),
                            displayName = "IMG_2026.jpg"
                        ),
                        paperSize = selectedPaper,
                        colorMode = selectedColor,
                        photoPrintSize = selectedSize,
                        multiPhotoLayout = MultiPhotoLayout.ONE_PER_SHEET
                    ),
                    usbState = UsbConnectionState(isChecking = false, attachedDeviceCount = 1),
                    printStatus = PrintJobStatus.Idle,
                    onBack = {},
                    onOpenSettings = {},
                    onOpenPrintSettings = {},
                    onSelectPaperSize = { selectedPaper = it },
                    onSelectColorMode = { selectedColor = it },
                    onSelectPhotoPrintSize = { selectedSize = it },
                    onSelectMultiPhotoLayout = {},
                    onPreviousPage = {},
                    onNextPage = {},
                    onPrint = { printPressed = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("paper_preview_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("photo_size_large").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("paper_a5").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_mode_bw").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("print_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(PhotoPrintSize.LARGE, selectedSize)
        assertEquals(PaperSize.A5, selectedPaper)
        assertEquals(ColorMode.BLACK_AND_WHITE, selectedColor)
        assertTrue(printPressed)
        bitmap.recycle()
    }
}
