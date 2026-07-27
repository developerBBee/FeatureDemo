package jp.developer.bbee.featuredemo.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.heifwriter.HeifWriter
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Bitmap を各画像形式のバイト列へ変換する。
 * PNG / JPEG / WebP は [Bitmap.compress]、BMP / GIF は自前エンコーダー、
 * HEIC は [HeifWriter](ハードウェアコーデック)を使用する。
 */
object ImageConverter {

    private const val DEFAULT_QUALITY = 95
    private const val HEIC_STOP_TIMEOUT_US = 10_000_000L

    fun convert(context: Context, source: Bitmap, format: ConversionFormat): ByteArray {
        val bitmap = source.asSoftwareArgb8888()
        return when (format) {
            ConversionFormat.PNG -> compress(bitmap, Bitmap.CompressFormat.PNG, 100)
            ConversionFormat.JPEG ->
                compress(bitmap.flattenAlphaToWhite(), Bitmap.CompressFormat.JPEG, DEFAULT_QUALITY)

            ConversionFormat.WEBP -> compressWebp(bitmap)
            ConversionFormat.GIF -> {
                val flattened = bitmap.flattenAlphaToWhite()
                GifEncoder.encode(flattened.toPixels(), flattened.width, flattened.height)
            }

            ConversionFormat.BMP -> {
                val flattened = bitmap.flattenAlphaToWhite()
                BmpEncoder.encode(flattened.toPixels(), flattened.width, flattened.height)
            }

            ConversionFormat.HEIC ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    encodeHeic(context, bitmap)
                } else {
                    throw UnsupportedOperationException("HEIC への変換は Android 9 以上が必要です")
                }
        }
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        check(bitmap.compress(format, quality, stream)) { "画像の圧縮に失敗しました" }
        return stream.toByteArray()
    }

    private fun compressWebp(bitmap: Bitmap): ByteArray =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            compress(bitmap, Bitmap.CompressFormat.WEBP_LOSSY, DEFAULT_QUALITY)
        } else {
            @Suppress("DEPRECATION")
            compress(bitmap, Bitmap.CompressFormat.WEBP, DEFAULT_QUALITY)
        }

    // HeifWriter はストリーム出力に対応していないため、キャッシュ上の一時ファイル経由で書き出す
    @RequiresApi(Build.VERSION_CODES.P)
    private fun encodeHeic(context: Context, bitmap: Bitmap): ByteArray {
        val tempFile = File.createTempFile("heic_convert", ".heic", context.cacheDir)
        try {
            val writer = HeifWriter.Builder(
                tempFile.absolutePath,
                bitmap.width,
                bitmap.height,
                HeifWriter.INPUT_MODE_BITMAP,
            )
                .setQuality(DEFAULT_QUALITY)
                .setMaxImages(1)
                .build()
            try {
                writer.start()
                writer.addBitmap(bitmap)
                writer.stop(HEIC_STOP_TIMEOUT_US)
            } finally {
                writer.close()
            }
            return tempFile.readBytes()
        } finally {
            tempFile.delete()
        }
    }

    /** HARDWARE ビットマップ等からピクセルを読み出せるよう ARGB_8888 のソフトウェア構成に揃える。 */
    private fun Bitmap.asSoftwareArgb8888(): Bitmap =
        if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)

    /** アルファ非対応の形式向けに、透過部分を白背景へ合成する。 */
    private fun Bitmap.flattenAlphaToWhite(): Bitmap {
        if (!hasAlpha()) return this
        val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(this, 0f, 0f, null)
        return flattened
    }

    private fun Bitmap.toPixels(): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }
}
