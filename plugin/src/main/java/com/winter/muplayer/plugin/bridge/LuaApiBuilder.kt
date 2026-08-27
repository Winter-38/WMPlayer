package com.winter.muplayer.plugin.bridge

import android.content.Context
import android.util.Base64
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.core.engine.ExoPlayerEngine
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.plugin.PluginUiBridge
import com.winter.muplayer.plugin.registry.ExportedFunctionMap
import com.winter.muplayer.plugin.runtime.HostFunction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 宿主 API 构建器：把应用真实能力桥接为 Lua 全局表 `wm`。
 *
 * 覆盖（贴合当前应用能力）：
 * - 通用：log / plugin / app / eventBus / thread / timer
 * - 播放器：player（桥接 MusicPlayerCore）
 * - 数据：json / url / crypto(哈希·base64·hex) / http
 * - 系统：system(音量) / clipboard
 *
 * 暂不桥接（应用当前无对应基础设施或需前置改造，见 docs/plugin-api.md）：
 * audio（无 PCM/FFT）、ui.registerComponent、buffer/iconv、crypto AES/RSA/HMAC、
 * system 媒体键/通知/壁纸/电池/屏幕。
 *
 * @param context 应用上下文（用于 app 信息、音量、剪贴板）
 * @param scope   宿主调度作用域（与播放核心一致的主线程 scope）
 */
class LuaApiBuilder(
    context: Context,
    private val scope: CoroutineScope,
    private val exported: ExportedFunctionMap,
    private val uiBridge: PluginUiBridge? = null,
) {
    private val appContext: Context = context.applicationContext
    private val core: MusicPlayerCore = MusicPlayerCore.getInstance(appContext)
    private val clipboardManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 构建完整宿主 API 表。 */
    fun build(session: PluginSession): LuaTable {
        val wm = LuaTable()
        wm.set("log", logApi())
        wm.set("plugin", pluginApi(session))
        wm.set("app", appApi())
        wm.set("eventBus", eventBusApi(session))
        wm.set("thread", threadApi())
        wm.set("timer", timerApi(session))
        wm.set("player", playerApi())
        wm.set("json", jsonApi())
        wm.set("url", urlApi())
        wm.set("crypto", cryptoApi())
        wm.set("http", httpApi())
        wm.set("clipboard", clipboardApi())
        wm.set("ui", uiApi(session))
        wm.set("services", servicesApi())
        return wm
    }

    // ==================== log ====================

    private fun logApi(): LuaTable {
        val log = LuaTable()
        log.set("d", HostFunction { a -> android.util.Log.d(a.arg(1).tojstring(), a.arg(2).tojstring()); LuaValue.NONE })
        log.set("i", HostFunction { a -> android.util.Log.i(a.arg(1).tojstring(), a.arg(2).tojstring()); LuaValue.NONE })
        log.set("w", HostFunction { a -> android.util.Log.w(a.arg(1).tojstring(), a.arg(2).tojstring()); LuaValue.NONE })
        log.set("e", HostFunction { a -> android.util.Log.e(a.arg(1).tojstring(), a.arg(2).tojstring()); LuaValue.NONE })
        return log
    }

    // ==================== plugin ====================

    private fun pluginApi(session: PluginSession): LuaTable {
        val plugin = LuaTable()
        plugin.set("getConfig", HostFunction { a ->
            session.configStore.get(a.arg(1).tojstring())
        })
        plugin.set("setConfig", HostFunction { a ->
            val key = a.arg(1).tojstring()
            val newValue = a.arg(2)
            val oldValue = session.configStore.set(key, newValue)
            session.callGlobalFunction("onConfigChanged",
                LuaValue.valueOf(key), newValue, oldValue)
            LuaValue.TRUE
        })
        plugin.set("getDataDir", HostFunction {
            LuaValue.valueOf(session.dataDir.absolutePath)
        })
        plugin.set("getCacheDir", HostFunction {
            LuaValue.valueOf(session.cacheDir.absolutePath)
        })
        return plugin
    }

    // ==================== app（只读） ====================

    private fun appApi(): LuaTable {
        val app = LuaTable()
        val versionName = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        } catch (_: Exception) { "0.0.0" }
        app.set("version", LuaValue.valueOf(versionName ?: "0.0.0"))
        app.set("platform", LuaValue.valueOf("android"))
        app.set("locale", LuaValue.valueOf(Locale.getDefault().language))
        return app
    }

    // ==================== eventBus ====================

    private fun eventBusApi(session: PluginSession): LuaTable {
        val bus = LuaTable()
        bus.set("on", HostFunction { a ->
            val event = a.arg(1).tojstring()
            val cb = a.arg(2).optfunction(null)
                ?: return@HostFunction LuaValue.FALSE
            session.eventBus.on(event, cb)
        })
        bus.set("emit", HostFunction { a ->
            session.eventBus.emit(a.arg(1).tojstring(), a.arg(2))
            LuaValue.NONE
        })
        return bus
    }

    // ==================== thread / timer ====================

    private fun threadApi(): LuaTable {
        val thread = LuaTable()
        // 注意：必须在本 scope（单线程 Lua 调度器）上排队执行，不能切 Dispatchers.IO ——
        // LuaJ Globals 非线程安全，fn 是 Lua 闭包，在 IO 线程池并发执行会与定时器/事件/
        // 播放器分发并发进入共享运行时。语义为“异步排队执行”（不阻塞当前调用）。
        thread.set("run", HostFunction { a ->
            a.arg(1).optfunction(null)?.let { scope.launch { safeInvoke(it) } }
            LuaValue.NONE
        })
        thread.set("ui", HostFunction { a ->
            a.arg(1).optfunction(null)?.let { scope.launch { safeInvoke(it) } }
            LuaValue.NONE
        })
        thread.set("sleep", HostFunction { a ->
            Thread.sleep(a.arg(1).tolong().coerceAtLeast(0L))
            LuaValue.NONE
        })
        return thread
    }

    private fun timerApi(session: PluginSession): LuaTable {
        val timer = LuaTable()
        timer.set("setTimeout", HostFunction { a ->
            val fn = a.arg(2).optfunction(null) ?: return@HostFunction LuaValue.NIL
            LuaValue.valueOf(session.scheduler.setTimeout(a.arg(1).tolong(), fn).toDouble())
        })
        timer.set("setInterval", HostFunction { a ->
            val fn = a.arg(2).optfunction(null) ?: return@HostFunction LuaValue.NIL
            LuaValue.valueOf(session.scheduler.setInterval(a.arg(1).tolong(), fn).toDouble())
        })
        timer.set("clear", HostFunction { a ->
            session.scheduler.clear(a.arg(1).tolong())
            LuaValue.NONE
        })
        return timer
    }

    private fun safeInvoke(fn: org.luaj.vm2.LuaFunction) {
        try { fn.invoke() } catch (t: Throwable) {
            android.util.Log.w("LuaPlugin", "线程回调失败: ${t.message}", t)
        }
    }

    // ==================== player（桥接 MusicPlayerCore） ====================

    private fun playerApi(): LuaTable {
        val player = LuaTable()

        // play([track])：无参播放当前，有参立即播放指定 Track
        player.set("play", HostFunction { a ->
            val trackArg = a.arg(1)
            if (trackArg.istable()) {
                core.playTrackTop(LuaValues.fromTrackTable(trackArg.checktable()))
            } else {
                core.play()
            }
            LuaValue.NONE
        })
        player.set("pause", HostFunction { core.pause(); LuaValue.NONE })
        player.set("stop", HostFunction { core.engine.stop(); LuaValue.NONE })
        player.set("next", HostFunction { core.playNext(); LuaValue.NONE })
        player.set("previous", HostFunction { core.playPrevious(); LuaValue.NONE })
        player.set("seekTo", HostFunction { a -> core.seekTo(a.arg(1).tolong()); LuaValue.NONE })

        player.set("setVolume", HostFunction { a ->
            val left = a.arg(1).tofloat()
            val right = a.arg(2).optdouble(left.toDouble()).toFloat()
            (core.engine as? ExoPlayerEngine)?.setVolume((left + right) / 2f)
            LuaValue.NONE
        })
        player.set("setSpeed", HostFunction { a ->
            (core.engine as? ExoPlayerEngine)?.setPlaybackSpeed(a.arg(1).tofloat())
            LuaValue.NONE
        })

        player.set("isPlaying", HostFunction {
            LuaValue.valueOf(core.playerState.value.state == PlayerState.PLAYING)
        })
        player.set("getPosition", HostFunction {
            LuaValue.valueOf(core.progressState.value.progress.toDouble())
        })
        player.set("getDuration", HostFunction {
            LuaValue.valueOf(core.progressState.value.duration.toDouble())
        })
        player.set("getCurrentTrack", HostFunction {
            core.playerState.value.currentTrack?.let { LuaValues.toTrackTable(it) } ?: LuaValue.NIL
        })
        player.set("getPlaylist", HostFunction {
            val list = LuaTable()
            core.queueManager.queue.value.forEachIndexed { i, entry ->
                list.set(i + 1, LuaValues.toTrackTable(entry.track))
            }
            list
        })
        player.set("addToQueue", HostFunction { a ->
            a.arg(1).opttable(null)?.let { core.addTrack(LuaValues.fromTrackTable(it)) }
            LuaValue.NONE
        })
        player.set("removeFromQueue", HostFunction { a ->
            core.removeTrack(a.arg(1).toint())
            LuaValue.NONE
        })
        player.set("shuffle", HostFunction {
            val next = if (core.playMode.value == PlayMode.SHUFFLE) PlayMode.SEQUENTIAL else PlayMode.SHUFFLE
            core.setPlayMode(next)
            LuaValue.TRUE
        })
        player.set("setRepeatMode", HostFunction { a ->
            val mode = when (a.arg(1).tojstring()) {
                "one" -> PlayMode.SINGLE_LOOP
                "all" -> PlayMode.REPEAT_ALL
                else -> PlayMode.SEQUENTIAL
            }
            core.setPlayMode(mode)
            LuaValue.NONE
        })
        return player
    }

    // ==================== json ====================

    private fun jsonApi(): LuaTable {
        val json = LuaTable()
        json.set("parse", HostFunction { a ->
            try {
                LuaJson.toLua(JSONObject(a.arg(1).tojstring()))
            } catch (_: Exception) {
                LuaValue.NIL
            }
        })
        json.set("stringify", HostFunction { a ->
            try {
                LuaValue.valueOf(LuaJson.toJson(a.arg(1)).toString())
            } catch (_: Exception) {
                LuaValue.valueOf("")
            }
        })
        return json
    }

    // ==================== url ====================

    private fun urlApi(): LuaTable {
        val url = LuaTable()
        url.set("encode", HostFunction { a ->
            LuaValue.valueOf(URLEncoder.encode(a.arg(1).tojstring(), "UTF-8"))
        })
        url.set("decode", HostFunction { a ->
            LuaValue.valueOf(URLDecoder.decode(a.arg(1).tojstring(), "UTF-8"))
        })
        url.set("parse", HostFunction { a ->
            val table = LuaTable()
            try {
                val query = a.arg(1).tojstring().substringAfter('?', "")
                if (query.isNotEmpty()) {
                    for (pair in query.split("&")) {
                        val (k, v) = pair.split("=", limit = 2).let {
                            it[0] to (it.getOrNull(1) ?: "")
                        }
                        table.set(URLDecoder.decode(k, "UTF-8"),
                            LuaValue.valueOf(URLDecoder.decode(v, "UTF-8")))
                    }
                }
            } catch (_: Exception) { }
            table
        })
        return url
    }

    // ==================== crypto ====================

    private fun cryptoApi(): LuaTable {
        val crypto = LuaTable()
        fun digest(algo: String, data: String): String {
            val md = MessageDigest.getInstance(algo)
            return md.digest(data.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
        crypto.set("md5", HostFunction { a -> LuaValue.valueOf(digest("MD5", a.arg(1).tojstring())) })
        crypto.set("sha1", HostFunction { a -> LuaValue.valueOf(digest("SHA-1", a.arg(1).tojstring())) })
        crypto.set("sha256", HostFunction { a -> LuaValue.valueOf(digest("SHA-256", a.arg(1).tojstring())) })
        crypto.set("base64Encode", HostFunction { a ->
            LuaValue.valueOf(Base64.encodeToString(a.arg(1).tojstring().toByteArray(), Base64.NO_WRAP))
        })
        crypto.set("base64Decode", HostFunction { a ->
            LuaValue.valueOf(String(Base64.decode(a.arg(1).tojstring(), Base64.NO_WRAP)))
        })
        crypto.set("hexEncode", HostFunction { a ->
            LuaValue.valueOf(a.arg(1).tojstring().toByteArray(Charsets.UTF_8)
                .joinToString("") { "%02x".format(it) })
        })
        crypto.set("hexDecode", HostFunction { a ->
            val hex = a.arg(1).tojstring()
            val out = StringBuilder()
            var i = 0
            while (i + 1 < hex.length) {
                out.append(hex.substring(i, i + 2).toInt(16).toChar())
                i += 2
            }
            LuaValue.valueOf(out.toString())
        })
        return crypto
    }

    // ==================== http ====================

    private fun httpApi(): LuaTable {
        val http = LuaTable()

        fun execute(request: Request): Map<String, Any?> {
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val headers = LinkedHashMap<String, Any?>()
                resp.headers.forEach { (k, v) -> headers[k] = v }
                return mapOf(
                    "statusCode" to resp.code,
                    "body" to body,
                    "headers" to headers
                )
            }
        }

        fun headersTable(headers: LuaValue?): Map<String, String> {
            val map = LinkedHashMap<String, String>()
            headers?.opttable(null)?.let { t ->
                for (k in t.keys()) {
                    val key = k.tojstring()
                    if (key.toIntOrNull() != null) continue
                    map[key] = t.get(k).tojstring()
                }
            }
            return map
        }

        /** 异步执行请求，结果通过回调（单线程 Lua 调度器）返回，不阻塞任何 Lua 调用。 */
        fun asyncCall(request: Request, cb: org.luaj.vm2.LuaFunction) {
            scope.launch(Dispatchers.IO) {
                val result = try {
                    execute(request)
                } catch (t: Throwable) {
                    mapOf(
                        "statusCode" to 0,
                        "body" to "",
                        "headers" to emptyMap<String, Any?>(),
                        "error" to (t.message ?: t.javaClass.simpleName)
                    )
                }
                scope.launch { cb.invoke(LuaValues.toLuaValue(result)) }
            }
        }

        http.set("get", HostFunction { a ->
            val url = a.arg(1).tojstring()
            val cb = a.arg(3).optfunction(null) ?: a.arg(2).optfunction(null)
            val headers = a.arg(2).opttable(null)
            if (cb == null) {
                android.util.Log.w("LuaPlugin", "http.get 需要回调参数: http.get(url[, headers], cb)")
                return@HostFunction LuaValue.NONE
            }
            val request = Request.Builder().url(url)
                .apply { headersTable(headers).forEach { (k, v) -> header(k, v) } }
                .get().build()
            asyncCall(request, cb)
            LuaValue.NONE
        })

        http.set("post", HostFunction { a ->
            val url = a.arg(1).tojstring()
            val body = a.arg(2).optjstring("")
            val cb = a.arg(4).optfunction(null) ?: a.arg(3).optfunction(null)
            val headers = a.arg(3).opttable(null)
            if (cb == null) {
                android.util.Log.w("LuaPlugin", "http.post 需要回调参数: http.post(url, body[, headers], cb)")
                return@HostFunction LuaValue.NONE
            }
            val request = Request.Builder().url(url)
                .apply { headersTable(headers).forEach { (k, v) -> header(k, v) } }
                .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            asyncCall(request, cb)
            LuaValue.NONE
        })

        http.set("postForm", HostFunction { a ->
            val url = a.arg(1).tojstring()
            val form = a.arg(2).opttable(null)
            val cb = a.arg(3).optfunction(null)
            if (cb == null) {
                android.util.Log.w("LuaPlugin", "http.postForm 需要回调参数: http.postForm(url, form, cb)")
                return@HostFunction LuaValue.NONE
            }
            val formBody = okhttp3.FormBody.Builder()
            form?.let { t ->
                for (k in t.keys()) {
                    val key = k.tojstring()
                    if (key.toIntOrNull() != null) continue
                    formBody.add(key, t.get(k).tojstring())
                }
            }
            val request = Request.Builder().url(url).post(formBody.build()).build()
            asyncCall(request, cb)
            LuaValue.NONE
        })

        http.set("download", HostFunction { a ->
            val url = a.arg(1).tojstring()
            val savePath = a.arg(2).tojstring()
            val onProgress = a.arg(3).optfunction(null)
            val request = Request.Builder().url(url).get().build()
            scope.launch(Dispatchers.IO) {
                try {
                    httpClient.newCall(request).execute().use { resp ->
                        val file = java.io.File(savePath)
                        file.parentFile?.mkdirs()
                        val total = resp.body?.contentLength() ?: 0L
                        resp.body?.byteStream()?.use { input ->
                            file.outputStream().use { output ->
                                val buf = ByteArray(8192)
                                var read: Int
                                var done = 0L
                                while (input.read(buf).also { read = it } != -1) {
                                    output.write(buf, 0, read)
                                    done += read
                                    if (onProgress != null && total > 0) {
                                        val ratio = done.toDouble() / total
                                        scope.launch {
                                            onProgress.invoke(LuaValue.valueOf(ratio))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("LuaPlugin", "http.download 失败: ${t.message}", t)
                }
            }
            LuaValue.NONE
        })
        return http
    }

    // ==================== clipboard ====================

    private fun clipboardApi(): LuaTable {
        val clipboard = LuaTable()
        clipboard.set("get", HostFunction {
            val clip = clipboardManager.primaryClip
            val text = clip?.getItemAt(0)?.text?.toString()
            if (text.isNullOrEmpty()) LuaValue.NIL else LuaValue.valueOf(text)
        })
        clipboard.set("set", HostFunction { a ->
            clipboardManager.setPrimaryClip(
                android.content.ClipData.newPlainText("lua-plugin", a.arg(1).tojstring())
            )
            LuaValue.NONE
        })
        return clipboard
    }

    // ==================== services（插件间服务调用） ====================

    /**
     * 服务调用：插件间通过导出函数互调（服务插件为其它插件提供函数 API）。
     *
     * 线程安全说明：所有 Lua 执行都在宿主单线程调度器上，无论目标插件是 shared 还是
     * dedicated，调用方线程直接 invoke 目标 LuaFunction 都不会并发进入其环境。
     */
    private fun servicesApi(): LuaTable {
        val services = LuaTable()

        // services.list(pluginId) → 函数名数组（服务发现）
        services.set("list", HostFunction { a ->
            val names = exported.names(a.arg(1).tojstring())
            val t = LuaTable()
            names.forEachIndexed { i, n -> t.set(i + 1, LuaValue.valueOf(n)) }
            t
        })

        // services.call(pluginId, fnName, ...) → 目标插件导出函数的返回值（多返回值完整透传）
        services.set("call", HostFunction { a ->
            val fn = exported.find(a.arg(1).tojstring(), a.arg(2).tojstring())
            if (fn == null) LuaValue.NIL else fn.invoke(a.subargs(3))
        })
        return services
    }

    // ==================== ui（插件组件 / 界面 / widget） ====================

    private fun uiApi(session: PluginSession): LuaTable {
        val ui = LuaTable()

        // 注册普通组件：{ title?, icon?, onClick?, onLongClick?, onSwipe? }
        ui.set("registerComponent", HostFunction { a ->
            val id = a.arg(1).tojstring()
            val cfg = a.arg(2).opttable(null) ?: return@HostFunction LuaValue.FALSE
            session.ui.components[id] = UiComponentConfig(
                id = id,
                title = optString(cfg, "title", id),
                icon = optStringOrNull(cfg, "icon"),
                onClick = resolveAction(cfg.get("onClick")),
                onLongClick = resolveAction(cfg.get("onLongClick")),
                onSwipe = resolveAction(cfg.get("onSwipe")),
                isSlot = false,
            )
            notifyUiChanged(session)
            LuaValue.TRUE
        })

        // 注册 slot 型组件（参考 fp-backdrop：自身作背景层，children 由布局 JSON 承载）
        ui.set("registerSlotComponent", HostFunction { a ->
            val id = a.arg(1).tojstring()
            val cfg = a.arg(2).opttable(null) ?: return@HostFunction LuaValue.FALSE
            session.ui.components[id] = UiComponentConfig(
                id = id,
                title = optString(cfg, "title", id),
                icon = optStringOrNull(cfg, "icon"),
                onClick = null,
                onLongClick = null,
                onSwipe = null,
                isSlot = true,
            )
            notifyUiChanged(session)
            LuaValue.TRUE
        })

        // 注册界面：layout 为插件 zip 内布局 JSON 的相对路径（格式与 main.json 一致）
        ui.set("registerPage", HostFunction { a ->
            val id = a.arg(1).tojstring()
            val cfg = a.arg(2).opttable(null) ?: return@HostFunction LuaValue.FALSE
            val layout = optString(cfg, "layout", "")
            if (layout.isBlank()) return@HostFunction LuaValue.FALSE
            session.ui.pages[id] = UiPageConfig(
                id = id,
                title = optString(cfg, "title", id),
                layoutPath = layout,
            )
            notifyUiChanged(session)
            LuaValue.TRUE
        })

        // 注册 widget：type = dropdown / bottom_sheet / dialog；layout 同上
        ui.set("registerWidget", HostFunction { a ->
            val id = a.arg(1).tojstring()
            val cfg = a.arg(2).opttable(null) ?: return@HostFunction LuaValue.FALSE
            val layout = optString(cfg, "layout", "")
            if (layout.isBlank()) return@HostFunction LuaValue.FALSE
            session.ui.widgets[id] = UiWidgetConfig(
                id = id,
                title = optString(cfg, "title", id),
                type = optString(cfg, "type", "dialog"),
                layoutPath = layout,
            )
            notifyUiChanged(session)
            LuaValue.TRUE
        })

        // 移除该插件注册的某个 UI 元素（组件/页面/widget）
        ui.set("unregister", HostFunction { a ->
            val id = a.arg(1).tojstring()
            session.ui.components.remove(id)
            session.ui.pages.remove(id)
            session.ui.widgets.remove(id)
            notifyUiChanged(session)
            LuaValue.TRUE
        })
        return ui
    }

    /** 通知宿主同步该插件的 UI 渲染器（wm.ui 注册/反注册后）。 */
    private fun notifyUiChanged(session: PluginSession) {
        uiBridge?.onPluginUiChanged(session.descriptor.id)
    }

    /** 读 Lua table 字符串字段；缺省返回 [default]。 */
    private fun optString(table: LuaTable, key: String, default: String): String {
        val v = table.get(key)
        return if (v.isnil()) default else v.tojstring()
    }

    /** 读 Lua table 字符串字段；nil 返回 null。 */
    private fun optStringOrNull(table: LuaTable, key: String): String? {
        val v = table.get(key)
        return if (v.isnil()) null else v.tojstring()
    }

    /** 解析手势动作：Lua 函数 / { page=.. } / { widget=.. } / { call=function }。 */
    private fun resolveAction(value: LuaValue): UiAction? = when {
        value.isnil() -> null
        value.isfunction() -> UiAction.Call(value.checkfunction())
        value.istable() -> {
            val t = value.checktable()
            when {
                !t.get("page").isnil() -> UiAction.OpenPage(t.get("page").tojstring())
                !t.get("widget").isnil() -> UiAction.OpenWidget(t.get("widget").tojstring())
                t.get("call").isfunction() -> UiAction.Call(t.get("call").checkfunction())
                else -> null
            }
        }
        else -> null
    }
}
