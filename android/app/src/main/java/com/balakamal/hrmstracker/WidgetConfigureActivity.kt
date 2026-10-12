package com.balakamal.hrmstracker

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WidgetConfigureActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var previewWidgetCard: LinearLayout
    private lateinit var btnThemeDark: LinearLayout
    private lateinit var btnThemeLight: LinearLayout
    private lateinit var btnThemeAmoled: LinearLayout
    private lateinit var btnThemeTransparent: LinearLayout

    private lateinit var txtThemeDark: TextView
    private lateinit var txtThemeLight: TextView
    private lateinit var txtThemeAmoled: TextView
    private lateinit var txtThemeTransparent: TextView

    private lateinit var txtOpacityValue: TextView
    private lateinit var seekbarOpacity: SeekBar
    private lateinit var chip0: TextView
    private lateinit var chip25: TextView
    private lateinit var chip50: TextView
    private lateinit var chip75: TextView
    private lateinit var chip100: TextView
    private lateinit var btnApply: Button
    private lateinit var btnBack: ImageButton

    private var selectedTheme = "DARK"
    private var selectedOpacity = 100

    companion object {
        private const val PREFS_NAME = "HRMS_PREFS"
        private const val KEY_WIDGET_THEME = "WidgetTheme"
        private const val KEY_WIDGET_OPACITY = "WidgetOpacity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.activity_widget_configure)

        // Find widget ID from intent
        intent?.extras?.let {
            appWidgetId = it.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        selectedTheme = sharedPrefs.getString(KEY_WIDGET_THEME, "DARK") ?: "DARK"
        selectedOpacity = sharedPrefs.getInt(KEY_WIDGET_OPACITY, 100)

        initViews()
        setupListeners()
        updateUIState()
    }

    private fun initViews() {
        previewWidgetCard = findViewById(R.id.preview_widget_card)
        btnThemeDark = findViewById(R.id.btn_theme_dark)
        btnThemeLight = findViewById(R.id.btn_theme_light)
        btnThemeAmoled = findViewById(R.id.btn_theme_amoled)
        btnThemeTransparent = findViewById(R.id.btn_theme_transparent)

        txtThemeDark = findViewById(R.id.txt_theme_dark)
        txtThemeLight = findViewById(R.id.txt_theme_light)
        txtThemeAmoled = findViewById(R.id.txt_theme_amoled)
        txtThemeTransparent = findViewById(R.id.txt_theme_transparent)

        txtOpacityValue = findViewById(R.id.txt_opacity_value)
        seekbarOpacity = findViewById(R.id.seekbar_opacity)
        chip0 = findViewById(R.id.chip_op_0)
        chip25 = findViewById(R.id.chip_op_25)
        chip50 = findViewById(R.id.chip_op_50)
        chip75 = findViewById(R.id.chip_op_75)
        chip100 = findViewById(R.id.chip_op_100)
        btnApply = findViewById(R.id.btn_apply)
        btnBack = findViewById(R.id.btn_back)

        seekbarOpacity.progress = selectedOpacity
        txtOpacityValue.text = "$selectedOpacity%"
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnThemeDark.setOnClickListener {
            selectedTheme = "DARK"
            updateUIState()
        }

        btnThemeLight.setOnClickListener {
            selectedTheme = "LIGHT"
            updateUIState()
        }

        btnThemeAmoled.setOnClickListener {
            selectedTheme = "AMOLED"
            updateUIState()
        }

        btnThemeTransparent.setOnClickListener {
            selectedTheme = "TRANSPARENT"
            updateUIState()
        }

        chip0.setOnClickListener {
            selectedOpacity = 0
            seekbarOpacity.progress = 0
            updateUIState()
        }

        chip25.setOnClickListener {
            selectedOpacity = 25
            seekbarOpacity.progress = 25
            updateUIState()
        }

        chip50.setOnClickListener {
            selectedOpacity = 50
            seekbarOpacity.progress = 50
            updateUIState()
        }

        chip75.setOnClickListener {
            selectedOpacity = 75
            seekbarOpacity.progress = 75
            updateUIState()
        }

        chip100.setOnClickListener {
            selectedOpacity = 100
            seekbarOpacity.progress = 100
            updateUIState()
        }

        seekbarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    selectedOpacity = progress
                    updateUIState()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnApply.setOnClickListener {
            applyAndSavePreferences()
        }
    }

    private fun updateUIState() {
        txtOpacityValue.text = "$selectedOpacity%"

        // Theme selection chip highlighting
        btnThemeDark.setBackgroundResource(if (selectedTheme == "DARK") R.drawable.bg_configure_chip_selected else R.drawable.bg_configure_chip)
        txtThemeDark.setTextColor(if (selectedTheme == "DARK") 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())

        btnThemeLight.setBackgroundResource(if (selectedTheme == "LIGHT") R.drawable.bg_configure_chip_selected else R.drawable.bg_configure_chip)
        txtThemeLight.setTextColor(if (selectedTheme == "LIGHT") 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())

        btnThemeAmoled.setBackgroundResource(if (selectedTheme == "AMOLED") R.drawable.bg_configure_chip_selected else R.drawable.bg_configure_chip)
        txtThemeAmoled.setTextColor(if (selectedTheme == "AMOLED") 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())

        btnThemeTransparent.setBackgroundResource(if (selectedTheme == "TRANSPARENT") R.drawable.bg_configure_chip_selected else R.drawable.bg_configure_chip)
        txtThemeTransparent.setTextColor(if (selectedTheme == "TRANSPARENT") 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())

        // Opacity preset highlighting
        val chips = listOf(chip0 to 0, chip25 to 25, chip50 to 50, chip75 to 75, chip100 to 100)
        for ((chip, value) in chips) {
            val isMatch = Math.abs(selectedOpacity - value) < 12
            chip.setBackgroundResource(if (isMatch) R.drawable.bg_configure_chip_selected else R.drawable.bg_configure_chip)
            chip.setTextColor(if (isMatch) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
        }

        // Live preview background
        val backgroundRes = when (selectedTheme) {
            "AMOLED" -> R.drawable.widget_bg_amoled
            "TRANSPARENT" -> R.drawable.widget_bg_transparent
            "LIGHT" -> when {
                selectedOpacity >= 90 -> R.drawable.widget_bg_light_100
                selectedOpacity >= 65 -> R.drawable.widget_bg_light_75
                selectedOpacity >= 40 -> R.drawable.widget_bg_light_50
                selectedOpacity >= 15 -> R.drawable.widget_bg_light_25
                else -> R.drawable.widget_bg_transparent
            }
            else -> when { // DARK
                selectedOpacity >= 90 -> R.drawable.widget_bg_dark_100
                selectedOpacity >= 65 -> R.drawable.widget_bg_dark_75
                selectedOpacity >= 40 -> R.drawable.widget_bg_dark_50
                selectedOpacity >= 15 -> R.drawable.widget_bg_dark_25
                else -> R.drawable.widget_bg_transparent
            }
        }
        previewWidgetCard.setBackgroundResource(backgroundRes)
    }

    private fun applyAndSavePreferences() {
        sharedPrefs.edit()
            .putString(KEY_WIDGET_THEME, selectedTheme)
            .putInt(KEY_WIDGET_OPACITY, selectedOpacity)
            .apply()

        // Trigger updates across all active widgets
        AttendanceAppWidgetProvider.triggerWidgetUpdate(this)

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(Activity.RESULT_OK, resultValue)
        } else {
            setResult(Activity.RESULT_OK)
        }
        finish()
    }
}
