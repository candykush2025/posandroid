package com.blackcode.poscandykush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * PeriPage-specific printer implementation
 * PeriPage printers are IMAGE-BASED, not text-based
 */
class PeriPagePrinter(private val context: Context) {

    companion object {
        private const val TAG = "PeriPagePrinter"
        private const val PRINTER_WIDTH = 384 // PeriPage paper width in pixels
        private const val LINE_HEIGHT = 24
    }

    /**
     * Convert text receipt to bitmap image for PeriPage printer
     */
    fun createReceiptBitmap(cartItems: List<CartItem>, total: Double, timestamp: String): Bitmap {
        // Calculate total height needed
        val headerHeight = 100
        val itemsHeight = cartItems.size * LINE_HEIGHT * 2 + 100
        val footerHeight = 100
        val totalHeight = headerHeight + itemsHeight + footerHeight

        // Create bitmap
        val bitmap = Bitmap.createBitmap(PRINTER_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Setup paint
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        val boldPaint = Paint(paint).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 24f
        }

        var yPosition = 40f

        // Draw header
        val title = "CANDY KUSH"
        val titleWidth = boldPaint.measureText(title)
        canvas.drawText(title, (PRINTER_WIDTH - titleWidth) / 2, yPosition, boldPaint)
        yPosition += 40

        // Draw separator
        canvas.drawLine(20f, yPosition, PRINTER_WIDTH - 20f, yPosition, paint)
        yPosition += 30

        // Draw items header
        canvas.drawText("ITEM", 20f, yPosition, paint)
        canvas.drawText("QTY", 200f, yPosition, paint)
        canvas.drawText("TOTAL", 280f, yPosition, paint)
        yPosition += 25

        canvas.drawLine(20f, yPosition, PRINTER_WIDTH - 20f, yPosition, paint)
        yPosition += 30

        // Draw items
        for (item in cartItems) {
            val itemName = if (item.name.length > 15) item.name.substring(0, 12) + "..." else item.name
            canvas.drawText(itemName, 20f, yPosition, paint)
            canvas.drawText(String.format("%.1f", item.quantity), 200f, yPosition, paint)
            canvas.drawText(String.format("฿%.2f", item.total), 280f, yPosition, paint)
            yPosition += LINE_HEIGHT * 1.5f
        }

        yPosition += 10
        canvas.drawLine(20f, yPosition, PRINTER_WIDTH - 20f, yPosition, paint)
        yPosition += 30

        // Draw total
        canvas.drawText("TOTAL:", 200f, yPosition, boldPaint)
        canvas.drawText(String.format("฿%.2f", total), 280f, yPosition, boldPaint)
        yPosition += 40

        // Draw QR code if link is provided
        val prefs = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
        val qrLink = prefs.getString("qr_link", "")
        if (!qrLink.isNullOrEmpty()) {
            generateQRCode(qrLink, 150)?.let { qrBitmap ->
                val qrX = (PRINTER_WIDTH - qrBitmap.width) / 2f
                canvas.drawBitmap(qrBitmap, qrX, yPosition, null)
                qrBitmap.recycle()
                yPosition += qrBitmap.height + 20f
            }
        }

        // Draw footer
        val footer = prefs.getString("receipt_footer", "Thank you!") ?: "Thank you!"
        val footerWidth = paint.measureText(footer)
        canvas.drawText(footer, (PRINTER_WIDTH - footerWidth) / 2, yPosition, paint)
        yPosition += 25

        val timestampWidth = paint.measureText(timestamp)
        canvas.drawText(timestamp, (PRINTER_WIDTH - timestampWidth) / 2, yPosition, paint)

        return bitmap
    }

    /**
     * Generate QR code bitmap from text/URL
     */
    private fun generateQRCode(text: String, size: Int = 200): Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("PeriPagePrinter", "Error generating QR code", e)
            null
        }
    }

    /**
     * Convert bitmap to PeriPage printer format
     * PeriPage uses a specific image format
     */
    fun convertBitmapToPeriPageData(bitmap: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()

        // PeriPage header commands
        baos.write(byteArrayOf(0x10.toByte(), 0xff.toByte(), 0xfe.toByte(), 0x01.toByte())) // PeriPage init

        val width = bitmap.width
        val height = bitmap.height

        // Send image size
        baos.write(byteArrayOf(0x1d.toByte(), 0x76.toByte(), 0x30.toByte(), 0x00.toByte()))
        baos.write(byteArrayOf((width / 8).toByte(), 0x00.toByte()))
        baos.write(byteArrayOf((height and 0xff).toByte(), ((height shr 8) and 0xff).toByte()))

        // Convert bitmap to monochrome data
        for (y in 0 until height) {
            var byteIndex = 0
            var currentByte: Byte = 0
            var bitIndex = 7

            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()

                if (gray < 128) { // Black pixel
                    currentByte = (currentByte.toInt() or (1 shl bitIndex)).toByte()
                }

                bitIndex--
                if (bitIndex < 0) {
                    baos.write(currentByte.toInt())
                    currentByte = 0
                    bitIndex = 7
                    byteIndex++
                }
            }

            // Write last byte if needed
            if (bitIndex < 7) {
                baos.write(currentByte.toInt())
            }
        }

        // PeriPage footer
        baos.write(byteArrayOf(0x1b.toByte(), 0x4a.toByte(), 0x40.toByte())) // Feed paper
        baos.write(byteArrayOf(0x1b.toByte(), 0x64.toByte(), 0x02.toByte())) // Feed 2 lines

        return baos.toByteArray()
    }

    /**
     * Create a simple test print bitmap
     */
    fun createTestPrintBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(PRINTER_WIDTH, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }

        var y = 40f

        val line1 = "Test Print"
        canvas.drawText(line1, (PRINTER_WIDTH - paint.measureText(line1)) / 2, y, paint)
        y += 40

        val line2 = "CANDY KUSH"
        canvas.drawText(line2, (PRINTER_WIDTH - paint.measureText(line2)) / 2, y, paint)
        y += 40

        val line3 = "Success!"
        canvas.drawText(line3, (PRINTER_WIDTH - paint.measureText(line3)) / 2, y, paint)

        return bitmap
    }

    /**
     * Convert plain text to bitmap image for PeriPage printer
     */
    fun createTextBitmap(text: String): Bitmap {
        val lines = text.split("\n")
        val totalHeight = lines.size * LINE_HEIGHT + 40

        // Create bitmap
        val bitmap = Bitmap.createBitmap(PRINTER_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Setup paint
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        var yPosition = 30f

        // Draw each line of text
        for (line in lines) {
            if (line.isNotEmpty()) {
                canvas.drawText(line, 20f, yPosition, paint)
            }
            yPosition += LINE_HEIGHT
        }

        return bitmap
    }
}
