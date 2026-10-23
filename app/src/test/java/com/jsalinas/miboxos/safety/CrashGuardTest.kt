package com.jsalinas.miboxos.safety

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.testutil.FakeApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CrashGuardTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val prefs = Prefs(app)

    private fun homeFor(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return app.packageManager.queryIntentActivities(home, 0).firstOrNull()?.activityInfo?.name
    }

    @Test fun `normalmente el boton Home abre MiBoxOs`() {
        assertFalse(CrashGuard.isSafeMode(app))
        assertEquals("com.jsalinas.miboxos.HomeAlias", homeFor())
    }

    @Test fun `dos cierres no alcanzan, tres en dos minutos pasan a modo seguro`() {
        val t = 1_000_000L
        assertFalse(CrashGuard.record(app, prefs, t))
        assertFalse(CrashGuard.record(app, prefs, t + 10_000))
        assertTrue(CrashGuard.record(app, prefs, t + 20_000))
        assertTrue(CrashGuard.isSafeMode(app))
        val pm = app.packageManager
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            pm.getComponentEnabledSetting(ComponentName(app, "com.jsalinas.miboxos.HomeAlias")))
    }

    @Test fun `cierres separados por mas de dos minutos no cuentan`() {
        val t = 1_000_000L
        CrashGuard.record(app, prefs, t)
        CrashGuard.record(app, prefs, t + 130_000)
        assertFalse(CrashGuard.record(app, prefs, t + 260_000))
        assertFalse(CrashGuard.isSafeMode(app))
    }

    @Test fun `la pantalla de emergencia lista apps y Reintentar vuelve a MiBoxOs`() {
        FakeApps.installDefaults(app)
        CrashGuard.setSafeMode(app, true)
        val activity = Robolectric.buildActivity(SafeHomeActivity::class.java).setup().get()
        val root = activity.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as LinearLayout
        val buttons = root.getChildAt(2) as LinearLayout
        (buttons.getChildAt(0) as Button).performClick()
        assertFalse(CrashGuard.isSafeMode(app))
        assertTrue(prefs.crashTimes.isEmpty())
        assertEquals("com.jsalinas.miboxos.MainActivity", shadowOf(app).nextStartedActivity.component!!.className)
    }
}
