package com.winter.muplayer.model

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String
)