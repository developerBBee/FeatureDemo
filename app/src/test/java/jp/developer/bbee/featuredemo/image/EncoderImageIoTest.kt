package jp.developer.bbee.featuredemo.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * 自前エンコーダーの出力を JDK 標準の ImageIO デコーダーで読み込み、
 * 自作の検証コードに依存しない独立した実装で正しくデコードできることを確認する。
 */
class EncoderImageIoTest {

    @Test
    fun `BMP出力をImageIOでデコードすると全ピクセルが一致する`() {
        val width = 123
        val height = 77
        val pixels = randomPixels(width * height, seed = 42)

        val image = decode(BmpEncoder.encode(pixels, width, height))

        assertEquals(width, image.width)
        assertEquals(height, image.height)
        assertAllPixels(image) { x, y -> pixels[y * width + x] }
    }

    @Test
    fun `BMP出力は行パディングが必要な幅でも正しくデコードできる`() {
        // 幅 3 → 行 9 バイト → 4 バイト境界への 3 バイトパディングが入る
        val width = 3
        val height = 5
        val pixels = randomPixels(width * height, seed = 7)

        val image = decode(BmpEncoder.encode(pixels, width, height))

        assertAllPixels(image) { x, y -> pixels[y * width + x] }
    }

    @Test
    fun `GIF出力をImageIOでデコードすると減色後の期待値と全ピクセルが一致する`() {
        val width = 123
        val height = 77
        val pixels = randomPixels(width * height, seed = 42)

        val image = decode(GifEncoder.encode(pixels, width, height))

        assertEquals(width, image.width)
        assertEquals(height, image.height)
        assertAllPixels(image) { x, y -> quantize(pixels[y * width + x]) }
    }

    @Test
    fun `LZW辞書リセットを跨ぐ大きなGIF出力もImageIOでデコードできる`() {
        // 高エントロピーな 300x300 のノイズ画像は LZW 辞書が 4096 個に達し、
        // クリアコードによる辞書リセット経路を通る
        val width = 300
        val height = 300
        val pixels = randomPixels(width * height, seed = 12345)

        val image = decode(GifEncoder.encode(pixels, width, height))

        assertAllPixels(image) { x, y -> quantize(pixels[y * width + x]) }
    }

    private fun decode(bytes: ByteArray): BufferedImage {
        val image = ImageIO.read(ByteArrayInputStream(bytes))
        assertNotNull("ImageIO が画像をデコードできませんでした", image)
        return image
    }

    private fun assertAllPixels(image: BufferedImage, expected: (x: Int, y: Int) -> Int) {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val expectedColor = expected(x, y)
                val actualColor = image.getRGB(x, y)
                assertEquals(
                    "ピクセル不一致 at ($x,$y): expected=%08X actual=%08X"
                        .format(expectedColor, actualColor),
                    expectedColor,
                    actualColor,
                )
            }
        }
    }

    /** 線形合同法による再現可能な擬似ランダムピクセル列。 */
    private fun randomPixels(count: Int, seed: Int): IntArray {
        var state = seed
        return IntArray(count) {
            state = state * 1103515245 + 12345
            (0xFF shl 24) or ((state ushr 8) and 0xFFFFFF)
        }
    }

    /** [GifEncoder] と同じ R3・G3・B2 減色を適用した期待色。 */
    private fun quantize(color: Int): Int {
        val r = ((color ushr 16) and 0xFF) ushr 5
        val g = ((color ushr 8) and 0xFF) ushr 5
        val b = (color and 0xFF) ushr 6
        return (0xFF shl 24) or ((r * 255 / 7) shl 16) or ((g * 255 / 7) shl 8) or (b * 255 / 3)
    }
}
