package com.winter.muplayer.model

/**
 * 播放器的播放模式～
 * 你想怎么听歌？顺序播、随机打乱、还是单曲循环？
 * 三种模式随便切换，总有一种适合你现在的心情！
 */
enum class PlayMode {
    /** 顺序播放～播完列表就停啦 */
    SEQUENTIAL,

    /** 随机打乱！每次切歌都像开盲盒一样刺激 */
    SHUFFLE,

    /** 单曲循环，这一首歌我可以听一百遍！ */
    SINGLE_LOOP,

    /** 列表循环～播完最后一首自动从头再来一遍 */
    REPEAT_ALL
}
