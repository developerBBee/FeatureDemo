package jp.developer.bbee.featuredemo.image

import android.os.Build

/**
 * 画像形式変換のターゲット形式。
 */
enum class ConversionFormat(
    val displayName: String,
    val mimeType: String,
    val extension: String,
    val description: String,
    private val minApiLevel: Int = 0,
) {
    PNG(
        displayName = "PNG",
        mimeType = "image/png",
        extension = "png",
        description = "可逆圧縮・透過対応",
    ),
    JPEG(
        displayName = "JPEG",
        mimeType = "image/jpeg",
        extension = "jpg",
        description = "非可逆圧縮・写真向け(透過は白背景に合成)",
    ),
    WEBP(
        displayName = "WebP",
        mimeType = "image/webp",
        extension = "webp",
        description = "非可逆圧縮・高圧縮率・透過対応",
    ),
    GIF(
        displayName = "GIF",
        mimeType = "image/gif",
        extension = "gif",
        description = "256色に減色されます(透過は白背景に合成)",
    ),
    BMP(
        displayName = "BMP",
        mimeType = "image/bmp",
        extension = "bmp",
        description = "無圧縮・ファイルサイズ大(透過は白背景に合成)",
    ),
    HEIC(
        displayName = "HEIC",
        mimeType = "image/heic",
        extension = "heic",
        description = "高効率圧縮(Android 9 以上)",
        minApiLevel = Build.VERSION_CODES.P,
    ),
    ;

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= minApiLevel
}
