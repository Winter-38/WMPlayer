package com.winter.muplayer.plugin.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.luaj.vm2.LuaFunction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 插件调度器（`thread` / `timer`）。
 *
 * - 后台/UI 线程执行：基于 [scope]（应用主调度 scope，与播放核心一致）；
 * - 定时器：setTimeout / setInterval / clear，回调在 [scope] 的线程执行
 *   （回调为 Lua 函数，需在其所属运行时的线程上安全调用）。
 */
class PluginScheduler(private val scope: CoroutineScope) {

    private val jobs = ConcurrentHashMap<Long, Job>()
    private val counter = AtomicLong(0)

    /** 在后台线程执行 Lua 函数。 */
    fun runBackground(fn: LuaFunction) {
        scope.launch(Dispatchers.IO) { safeInvoke(fn) }
    }

    /** 在 UI（主）线程执行 Lua 函数。 */
    fun runUi(fn: LuaFunction) {
        scope.launch { safeInvoke(fn) }
    }

    /** 延迟 [ms] 毫秒后执行一次，返回定时器 id。 */
    fun setTimeout(ms: Long, fn: LuaFunction): Long {
        val id = counter.incrementAndGet()
        jobs[id] = scope.launch {
            delay(ms)
            safeInvoke(fn)
            jobs.remove(id)
        }
        return id
    }

    /** 每隔 [ms] 毫秒执行一次，返回定时器 id（用于 clear）。 */
    fun setInterval(ms: Long, fn: LuaFunction): Long {
        val id = counter.incrementAndGet()
        jobs[id] = scope.launch {
            while (isActive) {
                delay(ms)
                safeInvoke(fn)
            }
        }
        return id
    }

    /** 取消定时器。 */
    fun clear(id: Long) {
        jobs.remove(id)?.cancel()
    }

    /** 取消全部定时器与任务（插件卸载时调用）。 */
    fun clearAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    /** 在单线程 Lua 调度器上执行任意宿主代码块（宿主侧执行 Lua 函数调用等）。 */
    fun runBlock(block: () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                android.util.Log.w("LuaPlugin", "调度代码块失败: ${t.message}", t)
            }
        }
    }

    private fun safeInvoke(fn: LuaFunction) {
        try {
            fn.invoke()
        } catch (t: Throwable) {
            android.util.Log.w("LuaPlugin", "调度回调失败: ${t.message}", t)
        }
    }
}
