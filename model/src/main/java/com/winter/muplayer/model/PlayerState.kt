package com.winter.muplayer.model

/**
 * 播放器现在处于什么状态呀？
 * 从空闲到加载、播放、暂停，再到可能遇到的错误，
 * 播放器的所有心情变化都用这个枚举来表示～
 */
enum class PlayerState {
    /** 啥也没干，乖乖等着呢 */
    IDLE,

    /** 正在加载歌曲……别急别急，马上就好！ */
    LOADING,

    /** 正在播放～跟着节奏摇起来！ */
    PLAYING,

    /** 暂停了，先歇一会儿 */
    PAUSED,

    /** 出错了……对不起嘛，我也不是故意的 (｡•́︿•̀｡) */
    ERROR
}
