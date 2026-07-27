package jp.developer.bbee.featuredemo.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class GifEncoderTest {

    @Test
    fun `ヘッダーにシグネチャとサイズ情報が書き込まれる`() {
        val bytes = GifEncoder.encode(IntArray(6) { 0xFFFF0000.toInt() }, width = 3, height = 2)

        assertEquals("GIF89a", String(bytes, 0, 6, Charsets.US_ASCII))
        assertEquals(3, readShortLe(bytes, 6))
        assertEquals(2, readShortLe(bytes, 8))
        assertEquals(0x3B, bytes.last().toInt() and 0xFF) // トレーラー
    }

    @Test
    fun `原色と白黒はパレットで正確に表現されラウンドトリップで一致する`() {
        val colors = listOf(
            0xFFFF0000.toInt(), // red
            0xFF00FF00.toInt(), // green
            0xFF0000FF.toInt(), // blue
            0xFFFFFFFF.toInt(), // white
            0xFF000000.toInt(), // black
        )
        for (color in colors) {
            val pixels = IntArray(16) { color }
            val decoded = decodeGif(GifEncoder.encode(pixels, width = 4, height = 4))
            assertEquals(16, decoded.size)
            decoded.forEach { assertEquals(color, it) }
        }
    }

    @Test
    fun `グラデーション画像が量子化誤差の範囲内でラウンドトリップする`() {
        val width = 64
        val height = 32
        val pixels = IntArray(width * height) { i ->
            argb(r = (i * 7) % 256, g = (i * 13) % 256, b = (i * 29) % 256)
        }

        val decoded = decodeGif(GifEncoder.encode(pixels, width, height))

        assertEquals(pixels.size, decoded.size)
        for (i in pixels.indices) {
            // R3・G3・B2 減色の期待値と完全一致すること
            assertEquals(expectedQuantized(pixels[i]), decoded[i])
            // 量子化誤差が理論上の上限内であること (R/G: 255/8, B: 255/4)
            assertTrue(colorDiff(pixels[i], decoded[i], shift = 16) <= 32)
            assertTrue(colorDiff(pixels[i], decoded[i], shift = 8) <= 32)
            assertTrue(colorDiff(pixels[i], decoded[i], shift = 0) <= 64)
        }
    }

    @Test
    fun `高エントロピー画像で辞書リセットを跨いでもラウンドトリップする`() {
        val width = 200
        val height = 200
        var seed = 12345
        val pixels = IntArray(width * height) {
            seed = seed * 1103515245 + 12345
            0xFF000000.toInt() or ((seed ushr 8) and 0xFFFFFF)
        }

        val decoded = decodeGif(GifEncoder.encode(pixels, width, height))

        assertEquals(pixels.size, decoded.size)
        for (i in pixels.indices) {
            assertEquals(expectedQuantized(pixels[i]), decoded[i])
        }
    }

    @Test
    fun `1ピクセルの画像もエンコードできる`() {
        val decoded = decodeGif(GifEncoder.encode(intArrayOf(0xFFFF0000.toInt()), 1, 1))
        assertEquals(1, decoded.size)
        assertEquals(0xFFFF0000.toInt(), decoded[0])
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** エンコーダーと同じ R3・G3・B2 減色を適用した期待色。 */
    private fun expectedQuantized(color: Int): Int {
        val r = ((color ushr 16) and 0xFF) ushr 5
        val g = ((color ushr 8) and 0xFF) ushr 5
        val b = (color and 0xFF) ushr 6
        return argb(r * 255 / 7, g * 255 / 7, b * 255 / 3)
    }

    private fun colorDiff(a: Int, b: Int, shift: Int): Int {
        val ca = (a ushr shift) and 0xFF
        val cb = (b ushr shift) and 0xFF
        return if (ca > cb) ca - cb else cb - ca
    }

    private fun readShortLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    // --- 検証用の GIF デコーダー ---

    /** エンコード結果をパースし、パレット適用済みの ARGB ピクセル列を返す。 */
    private fun decodeGif(data: ByteArray): IntArray {
        assertEquals("GIF89a", String(data, 0, 6, Charsets.US_ASCII))
        val width = readShortLe(data, 6)
        val height = readShortLe(data, 8)
        val packed = data[10].toInt() and 0xFF
        assertTrue("グローバルカラーテーブルが必要", packed and 0x80 != 0)
        val paletteSize = 2 shl (packed and 0x7)

        var pos = 13
        val palette = IntArray(paletteSize)
        for (i in 0 until paletteSize) {
            val r = data[pos++].toInt() and 0xFF
            val g = data[pos++].toInt() and 0xFF
            val b = data[pos++].toInt() and 0xFF
            palette[i] = argb(r, g, b)
        }

        assertEquals(0x2C, data[pos].toInt() and 0xFF)
        pos++
        assertEquals(0, readShortLe(data, pos)) // left
        assertEquals(0, readShortLe(data, pos + 2)) // top
        assertEquals(width, readShortLe(data, pos + 4))
        assertEquals(height, readShortLe(data, pos + 6))
        pos += 8
        assertEquals(0, data[pos].toInt() and 0xFF) // ローカルカラーテーブルなし
        pos++

        val minCodeSize = data[pos++].toInt() and 0xFF
        val compressed = ByteArrayOutputStream()
        while (true) {
            val blockLength = data[pos++].toInt() and 0xFF
            if (blockLength == 0) break
            compressed.write(data, pos, blockLength)
            pos += blockLength
        }
        assertEquals(0x3B, data[pos].toInt() and 0xFF)

        val indices = lzwDecode(compressed.toByteArray(), minCodeSize)
        assertEquals(width * height, indices.size)
        return IntArray(indices.size) { palette[indices[it]] }
    }

    private fun lzwDecode(data: ByteArray, minCodeSize: Int): List<Int> {
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var bitPos = 0

        fun readCode(): Int {
            var code = 0
            repeat(codeSize) { i ->
                val bit = (data[bitPos ushr 3].toInt() ushr (bitPos and 7)) and 1
                code = code or (bit shl i)
                bitPos++
            }
            return code
        }

        val output = ArrayList<Int>()
        var dict = ArrayList<IntArray>()

        fun resetDict() {
            dict = ArrayList(clearCode + 2)
            for (i in 0 until clearCode) dict.add(intArrayOf(i))
            dict.add(IntArray(0)) // clear
            dict.add(IntArray(0)) // eoi
            codeSize = minCodeSize + 1
        }

        resetDict()
        var previous: IntArray? = null
        while (true) {
            val code = readCode()
            if (code == clearCode) {
                resetDict()
                previous = null
                continue
            }
            if (code == eoiCode) break

            val entry = when {
                code < dict.size -> dict[code]
                code == dict.size && previous != null -> previous + previous[0]
                else -> throw IllegalStateException("不正な LZW コード: $code")
            }
            entry.forEach { output.add(it) }
            if (previous != null && dict.size < 4096) {
                dict.add(previous + entry[0])
            }
            previous = entry
            if (dict.size >= (1 shl codeSize) && codeSize < 12) {
                codeSize++
            }
        }
        return output
    }
}
