package com.widgey.util

import android.text.SpannableString
import android.text.Spanned
import androidx.core.text.HtmlCompat

object HtmlFormatter {

    /**
     * Parse HTML from Workflowy's API into a Spanned for display/editing.
     * Handles bold, italic, strikethrough, code, links, and HTML entities.
     */
    fun toSpanned(html: String?): Spanned {
        if (html.isNullOrEmpty()) return SpannableString("")
        val parsed = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        // fromHtml always appends a trailing newline — trim it
        var end = parsed.length
        while (end > 0 && parsed[end - 1] == '\n') end--
        return SpannableString.valueOf(if (end == parsed.length) parsed else parsed.subSequence(0, end))
    }

    /**
     * Convert a Spanned (from the editor) back to HTML for Workflowy's API.
     *
     * HtmlCompat.toHtml wraps lines in <p> tags and uses <strike>/<tt> instead of
     * Workflowy's <s>/<code>, so we clean those up here.
     */
    fun toHtml(spanned: Spanned): String {
        val raw = HtmlCompat.toHtml(spanned, HtmlCompat.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL)
        return raw
            // Unwrap paragraph tags, using newlines between them
            .replace(Regex("<p[^>]*>"), "")
            .replace("</p>", "\n")
            // Normalise to Workflowy-accepted tags
            .replace("<strike>", "<s>").replace("</strike>", "</s>")
            .replace("<tt>", "<code>").replace("</tt>", "</code>")
            .trimEnd()
    }

    /**
     * Strip all HTML tags and decode entities, returning plain text.
     * Used wherever we want text-only display (node names in lists, etc).
     */
    fun stripHtml(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .trimEnd('\n')
    }
}
