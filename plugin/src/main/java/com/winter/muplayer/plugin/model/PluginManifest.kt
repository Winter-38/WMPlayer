package com.winter.muplayer.plugin.model

import com.winter.muplayer.plugin.PluginException
import com.winter.muplayer.plugin.RuntimeKind
import org.json.JSONObject
import java.io.File

/**
 * 插件类别：
 * - [APP]：基本插件，由组件和服务构成，根目录 main.json 为其默认 GUI；
 * - [COMPONENT]：组件插件，主要提供组件用于扩展 app UI（默认界面常列出可用组件及效果）；
 * - [SERVICE]：服务插件，不提供界面功能，提供一个或多个服务（函数 API）供其它插件调用
 *   （默认界面常列出提供的函数 API 及作用）。
 *
 * 三类插件均可有默认界面：zip 根目录 main.json；缺失时宿主生成信息页。
 */
enum class PluginType { APP, COMPONENT, SERVICE }

/**
 * 插件描述文件（插件包内 plugin.json）。
 *
 * 约定插件包格式为 zip：
 * ```
 * plugin.json      # 本文件（id/type/entry/runtime 等）
 * main.json        # 默认界面布局（可选，格式与 main.json 一致；缺失时宿主生成信息页）
 * main.lua         # entry 指定的入口脚本
 * ...              # 其他资源（可选）
 * ```
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val type: PluginType = PluginType.APP,
    val entry: String = "main.lua",
    val runtime: RuntimeKind = RuntimeKind.SHARED,
    val events: List<String> = emptyList()
) {
    /** 绑定安装目录，得到可注册的插件描述。 */
    fun toDescriptor(dir: File): PluginDescriptor = PluginDescriptor(
        id = id,
        name = name,
        version = version,
        type = type,
        entry = entry,
        runtime = runtime,
        events = events,
        dir = dir
    )

    companion object {
        fun parse(text: String): PluginManifest {
            val obj = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw PluginException("plugin.json 不是合法 JSON: ${e.message}", e)
            }
            val id = obj.optString("id").trim()
            if (id.isEmpty()) throw PluginException("plugin.json 缺少 id 字段")
            return PluginManifest(
                id = id,
                name = obj.optString("name", id),
                version = obj.optString("version", "0.0.0"),
                type = when (obj.optString("type", "app")) {
                    "component" -> PluginType.COMPONENT
                    "service" -> PluginType.SERVICE
                    else -> PluginType.APP
                },
                entry = obj.optString("entry", "main.lua").trim(),
                runtime = if (obj.optString("runtime", "shared") == "dedicated") {
                    RuntimeKind.DEDICATED
                } else {
                    RuntimeKind.SHARED
                },
                events = obj.optJSONArray("events")
                    ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                    ?: emptyList()
            )
        }
    }
}

/** 已安装插件的运行时描述。 */
data class PluginDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val type: PluginType = PluginType.APP,
    val entry: String,
    val runtime: RuntimeKind,
    val events: List<String>,
    val dir: File
) {
    /** 入口脚本源文件。 */
    val entryFile: File get() = File(dir, entry)

    /** 默认界面布局文件（zip 根目录 main.json，可选）。 */
    val defaultLayoutFile: File get() = File(dir, "main.json")
}
