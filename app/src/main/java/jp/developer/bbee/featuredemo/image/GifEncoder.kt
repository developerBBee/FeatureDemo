package jp.developer.bbee.featuredemo.image

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * GIF89a エンコーダー(静止画 1 フレーム)。
 * Android の [android.graphics.Bitmap.compress] は GIF に対応していないため自前で書き出す。
 *
 * GIF は 1 フレームあたり最大 256 色のため、R 3bit・G 3bit・B 2bit の均等分割パレット
 * (256 色)へ減色し、インデックスカラー列を GIF 仕様の可変長 LZW で圧縮する。
 * ピクセルは ARGB_8888(1 int = 0xAARRGGBB)を受け取り、アルファは無視する。
 */
object GifEncoder {

    private const val MIN_CODE_SIZE = 8
    private const val CLEAR_CODE = 1 shl MIN_CODE_SIZE
    private const val EOI_CODE = CLEAR_CODE + 1
    private const val MAX_CODE_SIZE = 12
    private const val MAX_DICT_CODE = (1 shl MAX_CODE_SIZE) - 1

    fun encode(pixels: IntArray, width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0) { "width と height は正の値である必要があります" }
        require(width <= 0xFFFF && height <= 0xFFFF) { "GIF の最大サイズ (65535px) を超えています" }
        require(pixels.size == width * height) { "pixels のサイズが width * height と一致しません" }

        val out = ByteArrayOutputStream(pixels.size / 2 + 1024)

        // ヘッダー
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))

        // 論理スクリーン記述子
        writeShortLe(out, width)
        writeShortLe(out, height)
        // グローバルカラーテーブルあり・色解像度 8bit・テーブルサイズ 2^(7+1)=256
        out.write(0xF7)
        out.write(0) // 背景色インデックス
        out.write(0) // ピクセルアスペクト比

        // グローバルカラーテーブル (R3・G3・B2 の均等分割 256 色)
        for (i in 0 until 256) {
            out.write(((i ushr 5) and 0x7) * 255 / 7)
            out.write(((i ushr 2) and 0x7) * 255 / 7)
            out.write((i and 0x3) * 255 / 3)
        }

        // 画像記述子
        out.write(0x2C)
        writeShortLe(out, 0) // left
        writeShortLe(out, 0) // top
        writeShortLe(out, width)
        writeShortLe(out, height)
        out.write(0) // ローカルカラーテーブルなし・インターレースなし

        // 画像データ (LZW 圧縮)
        out.write(MIN_CODE_SIZE)
        writeLzw(out, quantize(pixels))
        out.write(0) // ブロックターミネーター

        out.write(0x3B) // トレーラー
        return out.toByteArray()
    }

    /** ARGB ピクセルをパレットインデックス (R3・G3・B2) へ減色する。 */
    private fun quantize(pixels: IntArray): ByteArray {
        val indices = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            indices[i] = (((r ushr 5) shl 5) or ((g ushr 5) shl 2) or (b ushr 6)).toByte()
        }
        return indices
    }

    /** GIF 仕様の可変長 LZW 圧縮。辞書が 4096 個に達したらクリアコードでリセットする。 */
    private fun writeLzw(out: OutputStream, indices: ByteArray) {
        val bits = BitWriter(out)
        var codeSize = MIN_CODE_SIZE + 1
        var maxCode = (1 shl codeSize) - 1
        var nextCode = EOI_CODE + 1
        var dict = HashMap<Int, Int>()

        // コードを書き出し、辞書の使用数が現在のビット幅を超えていたらビット幅を広げる。
        // ビット幅の切り替え位置がデコーダー側の辞書構築タイミングと一致するよう、
        // 判定には「書き出し時点の nextCode」を用いる
        fun emit(code: Int) {
            bits.write(code, codeSize)
            if (nextCode > maxCode && codeSize < MAX_CODE_SIZE) {
                codeSize++
                maxCode = (1 shl codeSize) - 1
            }
        }

        emit(CLEAR_CODE)
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val next = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or next
            val found = dict[key]
            if (found != null) {
                prefix = found
                continue
            }
            emit(prefix)
            if (nextCode <= MAX_DICT_CODE) {
                dict[key] = nextCode
                nextCode++
            } else {
                emit(CLEAR_CODE)
                dict = HashMap()
                nextCode = EOI_CODE + 1
                codeSize = MIN_CODE_SIZE + 1
                maxCode = (1 shl codeSize) - 1
            }
            prefix = next
        }
        emit(prefix)
        emit(EOI_CODE)
        bits.finish()
    }

    private fun writeShortLe(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    /** LSB ファーストでビット列を詰め、255 バイトごとのサブブロックとして書き出す。 */
    private class BitWriter(private val out: OutputStream) {
        private val block = ByteArray(255)
        private var blockLength = 0
        private var bitBuffer = 0
        private var bitCount = 0

        fun write(code: Int, size: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                writeByte(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        fun finish() {
            if (bitCount > 0) {
                writeByte(bitBuffer and 0xFF)
                bitBuffer = 0
                bitCount = 0
            }
            flushBlock()
        }

        private fun writeByte(value: Int) {
            block[blockLength++] = value.toByte()
            if (blockLength == block.size) flushBlock()
        }

        private fun flushBlock() {
            if (blockLength > 0) {
                out.write(blockLength)
                out.write(block, 0, blockLength)
                blockLength = 0
            }
        }
    }
}
