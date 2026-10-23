package com.jsalinas.miboxos.safety

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.jsalinas.miboxos.MainActivity
import com.jsalinas.miboxos.Prefs

/**
 * Inicio de emergencia: Views simples, sin Compose, fondos ni red, para que nada de lo que pudo
 * hacer fallar a MiBoxOs se repita acá. Lista las apps y permite reintentar MiBoxOs.
 */
class SafeHomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(21, 21, 23))
            setPadding(pad * 2, pad, pad * 2, pad)
        }
        root.addView(TextView(this).apply {
            text = "MiBoxOs se cerró varias veces seguidas"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Para que la TV no quede en negro, este es un inicio simple. Podés abrir tus apps o reintentar MiBoxOs."
            textSize = 16f
            setTextColor(Color.rgb(160, 160, 166))
            setPadding(0, pad / 3, 0, pad / 2)
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START }
        buttons.addView(Button(this).apply {
            id = View.generateViewId()
            text = "Reintentar MiBoxOs"
            setOnClickListener {
                CrashGuard.reset(this@SafeHomeActivity, Prefs(this@SafeHomeActivity))
                startActivity(Intent(this@SafeHomeActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
        })
        buttons.addView(Button(this).apply {
            text = "Ajustes de Android TV"
            setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })
        root.addView(buttons)
        root.addView(ListView(this).apply {
            adapter = ArrayAdapter(this@SafeHomeActivity, android.R.layout.simple_list_item_1, apps.map { it.loadLabel(packageManager).toString() })
            setOnItemClickListener { _, _, position, _ ->
                val pkg = apps[position].activityInfo.packageName
                (packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg))
                    ?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.getChildAt(2).requestFocus()
    }

    // Es un inicio: Atrás no sale
    @Deprecated("Atrás no hace nada en el inicio")
    @Suppress("DEPRECATION")
    override fun onBackPressed() = Unit
}
