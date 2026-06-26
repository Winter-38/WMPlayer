package com.winter.muplayer.plugin

/**
 * @deprecated 宿主 API 已通过 [com.winter.muplayer.plugin_runtime.IPlayerHost] 接口暴露（Shadow 架构）。
 * 此文件仅保留用于编译兼容，将在下个主版本中移除。
 */
@Deprecated(
    message = "Use com.winter.muplayer.plugin_runtime.IPlayerHost instead (Shadow architecture)",
    replaceWith = ReplaceWith("com.winter.muplayer.plugin_runtime.IPlayerHost")
)
class CoreHostApi