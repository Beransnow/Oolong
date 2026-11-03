package oolong.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Records the code a cold start and the main pages' scrolling run, from which the app's baseline
 * profile is made. Signed out, so it reads only public pages and does nothing as anyone.
 *
 * Runs on a connected device with `./gradlew :app:generateBaselineProfile`. The app it drives is
 * installed beside the app itself, under its own id, so a device's own install is not touched.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // The first launch shows the landing page
        device.wait(Until.findObject(By.text(SKIP_SIGN_IN)), LANDING_TIMEOUT)?.click()
        device.waitForIdle()
        flingList()

        openLink("oolong://forum/$FORUM")
        waitForList()
        flingList()
        openThread()
        waitForList()
        flingList()
        device.pressBack()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    /** Opens [link] in the app, over the pages already open, as a link from outside does. */
    private fun MacrobenchmarkScope.openLink(link: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(TARGET_PACKAGE)
        startActivityAndWait(intent)
    }

    private fun MacrobenchmarkScope.waitForList() {
        device.wait(Until.hasObject(By.scrollable(true)), LOAD_TIMEOUT)
        // The first page of the list arrives after the list itself
        device.wait(Until.hasObject(By.text(LONG_TEXT)), LOAD_TIMEOUT)
        device.waitForIdle()
    }

    /** Flings the page's largest list down and back up. */
    private fun MacrobenchmarkScope.flingList() {
        repeat(FLINGS) { fling(Direction.DOWN) }
        repeat(FLINGS) { fling(Direction.UP) }
    }

    private fun MacrobenchmarkScope.fling(direction: Direction) {
        // A list laid out again as it loads is found anew
        repeat(ATTEMPTS) {
            try {
                val list = largestList() ?: return
                // Clear of the gesture areas at the edges
                list.setGestureMargin(device.displayWidth / 5)
                list.fling(direction)
                device.waitForIdle()
                return
            } catch (_: StaleObjectException) {
                device.waitForIdle()
            }
        }
    }

    private fun MacrobenchmarkScope.largestList(): UiObject2? = device.findObjects(By.scrollable(true))
        .maxByOrNull { it.visibleBounds.width() * it.visibleBounds.height() }

    /** Opens a thread from the forum's list: a tall item in the middle third of the screen. */
    private fun MacrobenchmarkScope.openThread() {
        // Past the forum's header, to its threads
        fling(Direction.DOWN)
        val top = device.displayHeight / 3
        val bottom = device.displayHeight * 2 / 3
        repeat(ATTEMPTS) {
            try {
                val item = device.findObjects(By.clickable(true).hasDescendant(By.text(LONG_TEXT)))
                    .firstOrNull { it.visibleBounds.centerY() in top..bottom && it.visibleBounds.height() > device.displayHeight / 10 }
                    ?: return
                item.click()
                device.waitForIdle()
                return
            } catch (_: StaleObjectException) {
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "oolong.client.bp"
        const val SKIP_SIGN_IN = "暂不登录"
        const val FORUM = "minecraft"
        const val FLINGS = 3
        const val ATTEMPTS = 3
        const val LANDING_TIMEOUT = 5_000L
        const val LOAD_TIMEOUT = 10_000L

        /** Text as long as a thread's title, which a button's label never is. */
        val LONG_TEXT: Pattern = Pattern.compile(".{8,}")
    }
}
