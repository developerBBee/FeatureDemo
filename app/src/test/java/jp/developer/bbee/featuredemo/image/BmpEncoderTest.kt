package jp.developer.bbee.featuredemo.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BmpEncoderTest {

    @Test
    fun `ヘッダーにシグネチャとサイズ情報が書き込まれる`() {
        val pixels = IntArray(6) { 0xFF000000.toInt() }
        val bytes = BmpEncoder.encode(pixels, width = 3, height = 2)

        assertEquals('B'.code, bytes[0].toInt())
        assertEquals('M'.code, bytes[1].toInt())
        // rowBytes = 9 → パディング 3 → 行ストライド 12、ファイルサイズ = 54 + 12 * 2
        assertEquals(54 + 24, readIntLe(bytes, 2))
        assertEquals(54, readIntLe(bytes, 10)) // ピクセルデータオフセット
        assertEquals(40, readIntLe(bytes, 14)) // BITMAPINFOHEADER サイズ
        assertEquals(3, readIntLe(bytes, 18)) // width
        assertEquals(2, readIntLe(bytes, 22)) // height
        assertEquals(1, readShortLe(bytes, 26)) // planes
        assertEquals(24, readShortLe(bytes, 28)) // bpp
        assertEquals(0, readIntLe(bytes, 30)) // BI_RGB
        assertEquals(24, readIntLe(bytes, 34)) // imageSize
        assertEquals(bytes.size, 54 + 24)
    }

    @Test
    fun `ピクセルがBGR順・ボトムアップで書き込まれる`() {
        val red = 0xFFFF0000.toInt()
        val green = 0xFF00FF00.toInt()
        val blue = 0xFF0000FF.toInt()
        val white = 0xFFFFFFFF.toInt()
        // 上段: red, green / 下段: blue, white
        val bytes = BmpEncoder.encode(intArrayOf(red, green, blue, white), width = 2, height = 2)

        // rowBytes = 6 → パディング 2 → 行ストライド 8
        // 先頭行は画像の下段 (blue, white)
        assertBgr(bytes, 54, expectedB = 0xFF, expectedG = 0x00, expectedR = 0x00)
        assertBgr(bytes, 57, expectedB = 0xFF, expectedG = 0xFF, expectedR = 0xFF)
        assertEquals(0, bytes[60].toInt())
        assertEquals(0, bytes[61].toInt())
        // 次の行は画像の上段 (red, green)
        assertBgr(bytes, 62, expectedB = 0x00, expectedG = 0x00, expectedR = 0xFF)
        assertBgr(bytes, 65, expectedB = 0x00, expectedG = 0xFF, expectedR = 0x00)
    }

    @Test
    fun `widthとheightの積がIntをオーバーフローする場合は例外になる`() {
        // 65536 * 65536 は Int では 0 にラップするため、Int のままの比較では
        // 空配列が誤って受理されてしまう
        assertThrows(IllegalArgumentException::class.java) {
            BmpEncoder.encode(IntArray(0), width = 65536, height = 65536)
        }
    }

    private fun assertBgr(bytes: ByteArray, offset: Int, expectedB: Int, expectedG: Int, expectedR: Int) {
        assertEquals(expectedB, bytes[offset].toInt() and 0xFF)
        assertEquals(expectedG, bytes[offset + 1].toInt() and 0xFF)
        assertEquals(expectedR, bytes[offset + 2].toInt() and 0xFF)
    }

    private fun readShortLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
