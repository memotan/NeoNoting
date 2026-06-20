package com.neonoting.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

/**
 * GAS URL を入力する画面。
 * - ウィジェット配置時（APPWIDGET_CONFIGURE）に開く
 * - ランチャーからも開けるので、後から URL を変更できる
 */
class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 設定途中でキャンセルされてもウィジェットが追加されないように
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_config)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val input = findViewById<EditText>(R.id.input_gas_url)
        input.setText(Prefs.getGasUrl(this))

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val url = input.text.toString().trim()
            if (!url.startsWith("https://")) {
                Toast.makeText(this, "https:// で始まる GAS URL を入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.setGasUrl(this, url)
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()

            // 既存ウィジェットを更新
            TaskWidgetProvider.notifyDataChanged(this)

            // 配置フローなら OK を返してウィジェットを確定させる
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                setResult(RESULT_OK, result)
            }
            finish()
        }
    }
}
