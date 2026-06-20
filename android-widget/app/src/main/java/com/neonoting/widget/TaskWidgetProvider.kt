package com.neonoting.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlin.concurrent.thread

/**
 * ホーム画面ウィジェット本体。
 * - リスト部分は TaskWidgetService（RemoteViewsFactory）が供給
 * - 各行タップ → ACTION_TOGGLE で完了状態をトグル
 * - 更新ボタン → リスト再取得
 * - ヘッダタップ → NeoNoting 本体（PWA）を開く
 */
class TaskWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.neonoting.widget.ACTION_TOGGLE"
        const val ACTION_REFRESH = "com.neonoting.widget.ACTION_REFRESH"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_DONE = "task_done"

        // 本体 PWA。必要なら自分の GitHub Pages URL に変更する。
        const val APP_URL = "https://memotan.github.io/neonoting/"

        fun notifyDataChanged(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TaskWidgetProvider::class.java))
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(ctx, mgr, id)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_REFRESH -> notifyDataChanged(ctx)

            ACTION_TOGGLE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val newDone = !intent.getBooleanExtra(EXTRA_TASK_DONE, false)
                val gasUrl = Prefs.getGasUrl(ctx)
                // ネットワークは非同期で。終わったらリストを再取得して反映。
                val pending = goAsync()
                thread {
                    try {
                        GasClient.setDone(gasUrl, taskId, newDone)
                    } catch (_: Exception) {
                        // 失敗時は再取得で実状態に戻す（楽観更新の取り消し）
                    } finally {
                        notifyDataChanged(ctx)
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun updateWidget(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(ctx.packageName, R.layout.widget_task_list)

        // リストアダプタを接続
        val svc = Intent(ctx, TaskWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_list, svc)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // 行タップ用のテンプレート（fillInIntent と合成される）
        val toggleIntent = Intent(ctx, TaskWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePending = PendingIntent.getBroadcast(
            ctx, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_list, togglePending)

        // 更新ボタン
        val refreshIntent = Intent(ctx, TaskWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                ctx, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // ヘッダ → 本体 PWA を開く
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(APP_URL))
        views.setOnClickPendingIntent(
            R.id.widget_title,
            PendingIntent.getActivity(
                ctx, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        mgr.updateAppWidget(widgetId, views)
        mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
    }
}
