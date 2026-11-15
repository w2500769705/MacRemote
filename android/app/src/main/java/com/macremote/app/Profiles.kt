package com.macremote.app

/** 不同网站/播放器的快捷键。按键名对应 Mac 端 macremoted 的写法。 */
data class Profile(
    val id: String,
    val label: String,
    val playPause: String = "space",
    val back: String = "left",
    val forward: String = "right",
    val fullscreen: String = "f",
    val next: String?,
    val prev: String?,
)

val PROFILES = listOf(
    // Jellyfin 网页版：N / P 切下一集上一集（带 shift 发出的是大写 N，大小写两种写法都认）
    Profile("jellyfin", "Jellyfin", next = "shift+n", prev = "shift+p"),
    // B 站：[ ] 切上一集/下一集（分 P、番剧）
    Profile("bilibili", "B站", next = "bracketright", prev = "bracketleft"),
    // 其他网站（YouTube 的 shift+n 是下一个视频）；没有快捷键的就用触控板点
    Profile("web", "其他网站", next = "shift+n", prev = "shift+p"),
)

fun profileById(id: String?) = PROFILES.firstOrNull { it.id == id } ?: PROFILES[0]
