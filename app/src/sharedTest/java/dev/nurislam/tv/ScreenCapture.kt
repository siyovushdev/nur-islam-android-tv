package dev.nurislam.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

fun ComposeContentTestRule.captureRenderedNode(tag: String? = null): ImageBitmap {
    val node = if (tag == null) this.onRoot() else this.onNodeWithTag(tag)
    if (System.getProperty("nurIslam.screenshotDir") == null) return node.captureToImage()
    // PixelCopy has no real Surface on Robolectric. Draw the actual Android view with native Skia.
    val bounds = node.fetchSemanticsNode().layoutInfo.coordinates.boundsInWindow()
    lateinit var image: Bitmap
    this.runOnIdle {
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
        val view = activity.window.decorView
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(full))
        image = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
    }
    return image.asImageBitmap()
}
