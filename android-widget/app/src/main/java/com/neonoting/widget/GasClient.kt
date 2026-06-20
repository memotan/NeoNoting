package com.neonoting.widget

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** GAS から取得した 1 件のタスク。PWA 側の {id,name,done,tags,order} と同じ形。 */
data class Task(
    val id: String,
    val name: String,
    val done: Boolean,
    val tags: List<String>
)

/**
 * NeoNoting の GAS プロキシを直接叩くクライアント。
 *
 * GAS Web アプリは POST に対して 302 で script.googleusercontent.com へ
 * リダイレクトし、本体レスポンスはそのリダイレクト先（GET）が返す。
 * HttpURLConnection の自動リダイレクトはクロスホストや POST→GET で
 * 挙動が不安定なので、ここでは手動でリダイレクトを追う。
 *
 * すべてのメソッドはブロッキング。必ずワーカースレッドから呼ぶこと
 * （RemoteViewsFactory.onDataSetChanged / goAsync のバックグラウンド）。
 */
object GasClient {

    private const val MAX_REDIRECTS = 5
    private const val TIMEOUT_MS = 15000

    /** action:'list' を呼んでタスク配列を返す。 */
    fun listTasks(gasUrl: String): List<Task> {
        val body = JSONObject().put("action", "list")
        val data = call(gasUrl, body)
        val arr = data as? JSONArray ?: JSONArray()
        val out = ArrayList<Task>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Task(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    done = o.optBoolean("done", false),
                    tags = parseTags(o.optJSONArray("tags"))
                )
            )
        }
        return out
    }

    /** 完了状態を更新する。action:'update' / props:{done}. */
    fun setDone(gasUrl: String, id: String, done: Boolean) {
        val body = JSONObject()
            .put("action", "update")
            .put("id", id)
            .put("props", JSONObject().put("done", done))
        call(gasUrl, body)
    }

    private fun parseTags(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            // タグは {name,color} だが文字列の場合も一応許容
            val name = when (val v = arr.opt(i)) {
                is JSONObject -> v.optString("name")
                else -> v?.toString() ?: ""
            }
            if (name.isNotBlank()) out.add(name)
        }
        return out
    }

    /**
     * gasUrl へ JSON を POST し、{ok,data,error} の data を返す。
     * ok=false なら例外を投げる。
     */
    private fun call(gasUrl: String, body: JSONObject): Any {
        if (gasUrl.isBlank()) throw IllegalStateException("GAS URLが未設定です")

        var url = URL(gasUrl)
        var redirects = 0
        var method = "POST"
        val payload = body.toString().toByteArray(Charsets.UTF_8)

        while (true) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = method
                if (method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(payload) }
                }
            }

            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc == null || ++redirects > MAX_REDIRECTS) {
                    throw RuntimeException("リダイレクトが多すぎます")
                }
                // GAS のリダイレクト先は GET で本体を返す
                url = URL(url, loc)
                method = "GET"
                continue
            }

            val text = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            } finally {
                conn.disconnect()
            }

            if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }

            val json = JSONObject(text)
            if (!json.optBoolean("ok", false)) {
                throw RuntimeException(json.optString("error", "不明なエラー"))
            }
            return json.opt("data") ?: JSONObject()
        }
    }
}
