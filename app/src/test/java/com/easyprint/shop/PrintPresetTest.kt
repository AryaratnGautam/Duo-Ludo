package com.easyprint.shop

import com.easyprint.shop.printing.PaperSize
import com.easyprint.shop.printing.PhotoPrintSize
import com.easyprint.shop.printing.PrintPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintPresetTest {

    @Test
    fun `small medium large presets produce increasing physical dimensions on A4`() {
        val small = PrintPreset.computeSinglePhotoLayout(
            paperSize = PaperSize.A4,
            photoPrintSize = PhotoPrintSize.SMALL,
            imageWidth = 1000,
            imageHeight = 1400
        ).imageRectsMm.first()

        val medium = PrintPreset.computeSinglePhotoLayout(
            paperSize = PaperSize.A4,
            photoPrintSize = PhotoPrintSize.MEDIUM,
            imageWidth = 1000,
            imageHeight = 1400
        ).imageRectsMm.first()

        val large = PrintPreset.computeSinglePhotoLayout(
            paperSize = PaperSize.A4,
            photoPrintSize = PhotoPrintSize.LARGE,
            imageWidth = 1000,
            imageHeight = 1400
        ).imageRectsMm.first()

        val full = PrintPreset.computeSinglePhotoLayout(
            paperSize = PaperSize.A4,
            photoPrintSize = PhotoPrintSize.FULL_SHEET,
            imageWidth = 1000,
            imageHeight = 1400
        ).imageRectsMm.first()

        assertTrue(small.widthMm < medium.widthMm)
        assertTrue(medium.widthMm < large.widthMm)
        assertTrue(large.widthMm < full.widthMm)
        assertTrue(small.heightMm < medium.heightMm)
        assertTrue(medium.heightMm < large.heightMm)
        assertTrue(large.heightMm < full.heightMm)
    }

    @Test
    fun `landscape photo selects landscape sheet and preserves aspect ratio without cropping`() {
        val layout = PrintPreset.computeSinglePhotoLayout(
            paperSize = PaperSize.A4,
            photoPrintSize = PhotoPrintSize.MEDIUM,
            imageWidth = 4000,
            imageHeight = 3000
        )

        assertTrue(layout.isLandscapeSheet)
        assertEquals(PaperSize.A4.heightMm, layout.sheetWidthMm, 0.01f)
        assertEquals(PaperSize.A4.widthMm, layout.sheetHeightMm, 0.01f)

        val rect = layout.imageRectsMm.first()
        assertEquals(4f / 3f, rect.widthMm / rect.heightMm, 0.01f)
        // Medium landscape is 6 x 4 in (152.4 x 101.6 mm); 4:3 fits height at 101.6 mm without cropping
        assertEquals(101.6f, rect.heightMm, 0.1f)
        assertEquals(101.6f * (4f / 3f), rect.widthMm, 0.2f)
    }

    @Test
    fun `portrait photo centers inside A4 printable area`() {
        val layout = PrintPreset.computeSinglePhotoLayout(
            paperSize = PaperSize.A4,
            photoPrintSize = PhotoPrintSize.SMALL,
            imageWidth = 350,
            imageHeight = 500
        )

        assertFalse(layout.isLandscapeSheet)
        val rect = layout.imageRectsMm.first()
        val expectedCenterX = layout.sheetWidthMm / 2f
        val expectedCenterY = layout.sheetHeightMm / 2f
        val actualCenterX = rect.leftMm + rect.widthMm / 2f
        val actualCenterY = rect.topMm + rect.heightMm / 2f

        assertEquals(expectedCenterX, actualCenterX, 0.05f)
        assertEquals(expectedCenterY, actualCenterY, 0.05f)
        assertEquals(88.9f, rect.widthMm, 0.1f)
        assertEquals(127.0f, rect.heightMm, 0.1f)
    }

    @Test
    fun `combined layout places multiple photos inside printable margins without overlap`() {
        val sheets = PrintPreset.computeCombinedSheetPages(
            paperSize = PaperSize.A4,
            imageDimensions = listOf(
                1000 to 1500,
                1000 to 1500,
                1500 to 1000,
                1200 to 1200
            )
        )

        assertEquals(1, sheets.size)
        val rects = sheets.first().imageRectsMm
        assertEquals(4, rects.size)
        rects.forEach { rect ->
            assertTrue(rect.leftMm >= PrintPreset.SAFE_MARGIN_MM - 0.01f)
            assertTrue(rect.topMm >= PrintPreset.SAFE_MARGIN_MM - 0.01f)
            assertTrue(rect.rightMm <= sheets.first().sheetWidthMm - PrintPreset.SAFE_MARGIN_MM + 0.01f)
            assertTrue(rect.bottomMm <= sheets.first().sheetHeightMm - PrintPreset.SAFE_MARGIN_MM + 0.01f)
        }
    }
}
