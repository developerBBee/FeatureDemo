package jp.developer.bbee.featuredemo.image

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 24bit 無圧縮 BMP(Windows Bitmap)エンコーダー。
 * Android の [android.graphics.Bitmap.compress] は BMP に対応していないため自前で書き出す。
 * ピクセルは ARGB_8888(1 int = 0xAARRGGBB)を受け取り、アルファは無視する。
 */
object BmpEncoder {

    private const val FILE_HEADER_SIZE = 14
    private const val INFO_HEADER_SIZE = 40
    private const val PIXEL_DATA_OFFSET = FILE_HEADER_SIZE + INFO_HEADER_SIZE

    // 72dpi 相当 (2835 pixels/meter)
    private const val PIXELS_PER_METER = 2835

    fun encode(pixels: IntArray, width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0) { "width と height は正の値である必要があります" }
        // width * height は Int でオーバーフローし得るため Long で比較する
        require(pixels.size.toLong() == width.toLong() * height) { "pixels のサイズが width * height と一致しません" }

        val rowBytes = width * 3
        // 各行は 4 バイト境界にパディングされる
        val padding = (4 - rowBytes % 4) % 4
        val imageSize = (rowBytes + padding) * height
        val fileSize = PIXEL_DATA_OFFSET + imageSize

        val buffer = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN)

        // BITMAPFILEHEADER
        buffer.put('B'.code.toByte())
        buffer.put('M'.code.toByte())
        buffer.putInt(fileSize)
        buffer.putInt(0) // 予約領域
        buffer.putInt(PIXEL_DATA_OFFSET)

        // BITMAPINFOHEADER
        buffer.putInt(INFO_HEADER_SIZE)
        buffer.putInt(width)
        buffer.putInt(height) // 正の値 = ボトムアップ格納
        buffer.putShort(1) // planes
        buffer.putShort(24) // bits per pixel
        buffer.putInt(0) // BI_RGB (無圧縮)
        buffer.putInt(imageSize)
        buffer.putInt(PIXELS_PER_METER)
        buffer.putInt(PIXELS_PER_METER)
        buffer.putInt(0) // 使用色数 (0 = 全色)
        buffer.putInt(0) // 重要色数

        // ピクセルデータ: ボトムアップ・BGR 順
        for (y in height - 1 downTo 0) {
            var index = y * width
            repeat(width) {
                val pixel = pixels[index++]
                buffer.put((pixel and 0xFF).toByte()) // B
                buffer.put(((pixel ushr 8) and 0xFF).toByte()) // G
                buffer.put(((pixel ushr 16) and 0xFF).toByte()) // R
            }
            repeat(padding) { buffer.put(0) }
        }

        return buffer.array()
    }
}
