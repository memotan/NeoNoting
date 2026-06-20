package com.neonoting.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** リスト用の RemoteViewsFactory を返すだけのサービス。 */
class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TaskRemoteViewsFactory(applicationContext)
}

/**
 * GAS からタスクを取得してリスト各行の RemoteViews を生成する。
 * onDataSetChanged はバインダースレッドで呼ばれるため、同期ネットワークでよい。
 */
class TaskRemoteViewsFactory(
    private val ctx: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var tasks: List<Task> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val gasUrl = Prefs.getGasUrl(ctx)
        tasks = try {
            // 未完了を上に、完了済みを下に。本体の表示順に寄せる。
            GasClient.listTasks(gasUrl).sortedBy { it.done }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() { tasks = emptyList() }

    override fun getCount(): Int = tasks.size

    override fun getViewAt(position: Int): RemoteViews {
        val task = tasks[position]
        val row = RemoteViews(ctx.packageName, R.layout.widget_task_item)

        row.setTextViewText(R.id.item_name, task.name)
        row.setImageViewResource(
            R.id.item_check,
            if (task.done) R.drawable.ic_check_on else R.drawable.ic_check_off
        )

        // 完了済みは薄字で区別（RemoteViews では取り消し線=setPaintFlags が
        // 非対応のため、色のみで表現する）
        row.setTextColor(
            R.id.item_name,
            if (task.done) 0xFF6B7280.toInt() else 0xFFE5E7EB.toInt()
        )

        val tagText = task.tags.joinToString(" ") { "#$it" }
        row.setTextViewText(R.id.item_tags, tagText)
        row.setViewVisibility(
            R.id.item_tags,
            if (tagText.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        )

        // タップで完了トグル。テンプレートと合成される fillInIntent。
        val fill = Intent().apply {
            putExtra(TaskWidgetProvider.EXTRA_TASK_ID, task.id)
            putExtra(TaskWidgetProvider.EXTRA_TASK_DONE, task.done)
        }
        row.setOnClickFillInIntent(R.id.item_root, fill)

        return row
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tasks[position].id.hashCode().toLong()
    override fun hasStableIds(): Boolean = true
}
