package com.b2binventory.app.ui.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Translates English UI text into the app's current language.
 *
 * Screens are written with English text; [tr] looks the text up in [UiCatalog] and returns the string
 * resource for the active language. Texts built from a template (e.g. "Only 5 in stock") are matched against
 * the template ("Only %1$s in stock") and translated with the same values filled in. Lines and " · " separated
 * parts are translated piece by piece. Anything unknown, such as product names or messages written by the
 * server, is returned unchanged.
 */
object I18n {

    private const val SEPARATOR = " · "

    private lateinit var app: Context

    private class Template(val regex: Regex, val id: Int)

    private val exact = HashMap<String, Int>(UiCatalog.texts.size * 2)
    private val templates = ArrayList<Template>()

    private var cachedTag: String? = null
    private var localized: Context? = null
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 1000
    }

    fun init(context: Context) {
        app = context.applicationContext
        val placeholder = Regex("%\\d+\\\$s")
        UiCatalog.texts.forEachIndexed { i, text ->
            val english = text.replace("%%", "%")
            if (!placeholder.containsMatchIn(text)) {
                exact[english] = UiCatalog.ids[i]
                return@forEachIndexed
            }
            val literal = placeholder.split(text)
            // Templates that are almost only placeholders ("%1$s of %2$s") would match ordinary data.
            if (literal.sumOf { part -> part.count { it.isLetter() } } < 4) return@forEachIndexed
            val pattern = literal.joinToString("(.+?)") { Regex.escape(it.replace("%%", "%")) }
            templates += Template(Regex("^$pattern$", RegexOption.DOT_MATCHES_ALL), UiCatalog.ids[i])
        }
        // Longer templates first, so the most specific one wins.
        templates.sortByDescending { it.regex.pattern.length }
    }

    /** The language the app shows: the in-app choice, or the phone's language. */
    private fun currentLocale(): Locale {
        val chosen = AppCompatDelegate.getApplicationLocales()
        return if (!chosen.isEmpty) chosen[0]!! else Locale.getDefault()
    }

    private fun context(): Context? {
        val locale = currentLocale()
        val tag = locale.toLanguageTag()
        if (tag != cachedTag) {
            cachedTag = tag
            cache.clear()
            localized = if (locale.language == "en" || !::app.isInitialized) null
            else app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(locale) })
        }
        return localized
    }

    /** Translates [text] into the current language; returns it unchanged when English or unknown. */
    @Synchronized
    fun tr(text: String): String {
        if (text.isBlank()) return text
        val ctx = context() ?: return text
        return cache.getOrPut(text) { translate(ctx, text) }
    }

    /** Translates a template directly, e.g. `I18n.tr("Saved %1$s", name)`. */
    fun tr(template: String, vararg args: Any): String = tr(template.format(*args))

    private fun translate(ctx: Context, text: String): String {
        val start = text.indexOfFirst { !it.isWhitespace() }
        val end = text.indexOfLast { !it.isWhitespace() } + 1
        val core = text.substring(start, end)
        val translated = whole(ctx, core)
            ?: if (core.contains('\n')) core.split('\n').joinToString("\n") { line -> translate(ctx, line) }
            else if (core.contains(SEPARATOR)) core.split(SEPARATOR).joinToString(SEPARATOR) { part -> whole(ctx, part.trim()) ?: part }
            else core
        return text.substring(0, start) + translated + text.substring(end)
    }

    private fun whole(ctx: Context, text: String): String? {
        if (text.isEmpty()) return text
        exact[text]?.let { return ctx.getString(it).replace("%%", "%") }
        for (t in templates) {
            val m = t.regex.matchEntire(text) ?: continue
            val args = m.groupValues.drop(1).map { arg -> exact[arg]?.let { ctx.getString(it) } ?: arg }
            return runCatching { ctx.getString(t.id, *args.toTypedArray()) }.getOrNull()
        }
        return null
    }
}
