package com.winter.muplayer.model

/**
 * 播放列表的数据模型～
 * 每个播放列表就是一组曲目 ID 的集合，按添加顺序排列。
 * 数据会持久化到本地的 JSON 文件里，重启 App 也不会丢哦！
 *
 * @param id 每个播放列表都有自己的唯一 ID，创建的时候自动生成
 * @param name 列表的名字～默认是"未命名播放列表"
 * @param trackIds 里面装的曲目 ID 列表，按用户添加的顺序排列
 * @param createTime 啥时候创建的？时间戳，单位毫秒
 */
data class Playlist(
    val id: Long,
    val name: String,
    val trackIds: List<Long>,
    val createTime: Long
)
