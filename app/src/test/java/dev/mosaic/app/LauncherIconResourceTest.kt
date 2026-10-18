package dev.mosaic.app

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The external launcher/APK icon is the generated mipmap set sourced from
 * `mosaic_logo.svg`; the in-app vector branding must stay untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LauncherIconResourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun manifestUsesTheGeneratedLauncherMipmap() {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(R.mipmap.ic_launcher, appInfo.icon)
    }

    @Test
    fun launcherResourcesExist() {
        val resources = context.resources
        val packageName = context.packageName
        assertNotEquals(0, resources.getIdentifier("ic_launcher", "mipmap", packageName))
        assertNotEquals(0, resources.getIdentifier("ic_launcher_round", "mipmap", packageName))
        assertNotEquals(0, resources.getIdentifier("ic_launcher_foreground", "mipmap", packageName))
        assertNotEquals(0, resources.getIdentifier("ic_launcher_background", "color", packageName))
    }

    @Test
    fun launcherIconResolvesToAnAdaptiveIcon() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val drawable = context.getDrawable(R.mipmap.ic_launcher)
        assertTrue(drawable is AdaptiveIconDrawable)
    }

    @Test
    fun inAppVectorBrandingIsPreserved() {
        assertNotEquals(
            0,
            context.resources.getIdentifier("ic_mosaic", "drawable", context.packageName),
        )
    }
}
