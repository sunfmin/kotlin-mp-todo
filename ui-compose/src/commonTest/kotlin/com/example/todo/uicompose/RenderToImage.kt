package com.example.todo.uicompose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorAlphaType
import java.io.File

/**
 * Glue helper (render-ui-test rule 2): renders [content] through the real Compose
 * tree and encodes the result to a PNG file. Returns the absolute path.
 */
@OptIn(ExperimentalTestApi::class)
fun renderToImage(name: String, content: @Composable () -> Unit): String {
    lateinit var path: String
    runDesktopComposeUiTest(width = 400, height = 800) {
        setContent { MaterialTheme { Surface(modifier = Modifier.fillMaxSize()) { content() } } }
        val img: androidx.compose.ui.graphics.ImageBitmap = captureToImage()
        val pm = img.toPixelMap()
        val bytes = ByteArray(pm.width * pm.height * 4)
        var i = 0
        for (y in 0 until pm.height) {
            for (x in 0 until pm.width) {
                val c = pm[x, y]
                bytes[i++] = ((c.red * 255).toInt()).toByte()
                bytes[i++] = ((c.green * 255).toInt()).toByte()
                bytes[i++] = ((c.blue * 255).toInt()).toByte()
                bytes[i++] = ((c.alpha * 255).toInt()).toByte()
            }
        }
        val bitmap = Bitmap().apply {
            allocN32Pixels(pm.width, pm.height)
            installPixels(ImageInfo.makeN32(pm.width, pm.height, ColorAlphaType.UNPREMUL), bytes, pm.width * 4)
        }
        val data = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!
        val outDir = File("build/render-out").apply { mkdirs() }
        path = "$outDir/$name.png"
        File(path).writeBytes(data.bytes)
    }
    return path
}
