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
    val cid = entry.cid ?: return base
    val cidRules = css.rules["#${cid}"] ?: return base
    return base + cidRules
}
