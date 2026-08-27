package com.winter.muplayer.config

/**
 * 解析组件 CSS：type（#id）规则为基底，cid（#cid）规则覆盖。
 *
 * 合并语义为「属性级叠加」：
 * - 同名属性以 cid 为准（cid 覆盖 type）
 * - 非同名属性保留（type 的属性不会丢失）
 *
 * 对应文档 docs/wmplayer-config.md 3.1 节：
 * `#<cid>` 对 `#<type>` 是唯一例外：二者按属性级叠加，同名属性以 cid 为准，非同名属性保留。
 */
internal fun resolveComponentCss(entry: ComponentEntry, css: CssRuleTable): Map<String, String> {
    val base = css.rules["#${entry.id}"] ?: css.rules[entry.id] ?: emptyMap()
    val cid = entry.cid
    val resolved = if (cid != null) {
        val cidRules = css.rules["#${cid}"] ?: base
        base + cidRules
    } else {
        base
    }
    // 组件内联 style（布局 JSON 组件声明里的 "style": {...}）覆盖 CSS 规则，优先级最高
    val inline = entry.extra["style"]
    if (inline is org.json.JSONObject) {
        val map = mutableMapOf<String, String>()
        val it = inline.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = inline.opt(k)
            if (v != null) map[k] = v.toString()
        }
        return resolved + map
    }
    return resolved
}
