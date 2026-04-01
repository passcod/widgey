package com.widgey.util

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import java.util.TreeSet

object HtmlFormatter {

    /**
     * Parse HTML from Workflowy's API into a Spanned for display/editing.
     * Handles bold, italic, strikethrough, code, links, and HTML entities.
     *
     * Raw newlines (\n) in the HTML source are converted to <br> before parsing
     * so that HtmlCompat treats them as line breaks rather than collapsing them
     * to spaces (which is standard HTML whitespace behaviour).
     */
    fun toSpanned(html: String?): Spanned {
        if (html.isNullOrEmpty()) return SpannableString("")
        // Convert raw newlines to <br> so HtmlCompat preserves them
        val withBreaks = html.replace("\n", "<br>")
        val parsed = HtmlCompat.fromHtml(withBreaks, HtmlCompat.FROM_HTML_MODE_LEGACY)
        // fromHtml always appends a trailing newline — trim it
        var end = parsed.length
        while (end > 0 && parsed[end - 1] == '\n') end--
        return SpannableString.valueOf(if (end == parsed.length) parsed else parsed.subSequence(0, end))
    }

    /**
     * Convert a Spanned (from the editor) back to HTML for Workflowy's API.
     *
     * We do NOT use HtmlCompat.toHtml() here because it:
     *   - wraps every line in <p> tags (losing plain newlines)
     *   - encodes all non-ASCII characters as &#NNN; entities, which
     *     Workflowy does not support for arbitrary Unicode
     *
     * Instead we walk the span boundaries ourselves, emitting only the four
     * structural HTML escapes (<, >, &, ") and leaving all Unicode as-is.
     */
    fun toHtml(spanned: Spanned): String {
        val text = spanned.toString()
        val len = spanned.length
        if (len == 0) return ""

        // Collect all span boundary positions so we can emit one chunk per segment
        val boundaries = TreeSet<Int>().apply {
            add(0)
            add(len)
            for (span in spanned.getSpans(0, len, Any::class.java)) {
                add(spanned.getSpanStart(span))
                add(spanned.getSpanEnd(span))
            }
        }
        val points = boundaries.toList()

        val sb = StringBuilder()
        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]
            if (start >= end) continue

            // Spans active over this entire segment
            val active = spanned.getSpans(start, end, Any::class.java)
                .filter { spanned.getSpanStart(it) <= start && spanned.getSpanEnd(it) >= end }

            // Open tags
            for (span in active) sb.append(openTag(span) ?: continue)

            // Text — escape only structural HTML characters, keep all Unicode
            for (ch in text.substring(start, end)) {
                when (ch) {
                    '<'  -> sb.append("&lt;")
                    '>'  -> sb.append("&gt;")
                    '&'  -> sb.append("&amp;")
                    '"'  -> sb.append("&quot;")
                    else -> sb.append(ch)   // includes \n, emoji, accents, CJK, …
                }
            }

            // Close tags in reverse order so nesting is valid
            for (span in active.reversed()) sb.append(closeTag(span) ?: continue)
        }

        return sb.toString()
    }

    private fun openTag(span: Any): String? = when (span) {
        is StyleSpan -> when (span.style) {
            Typeface.BOLD        -> "<b>"
            Typeface.ITALIC      -> "<i>"
            Typeface.BOLD_ITALIC -> "<b><i>"
            else                 -> null
        }
        is StrikethroughSpan -> "<s>"
        is TypefaceSpan      -> if (span.family == "monospace") "<code>" else null
        is URLSpan           -> "<a href=\"${span.url.replace("\"", "&quot;")}\">"
        else                 -> null
    }

    private fun closeTag(span: Any): String? = when (span) {
        is StyleSpan -> when (span.style) {
            Typeface.BOLD        -> "</b>"
            Typeface.ITALIC      -> "</i>"
            Typeface.BOLD_ITALIC -> "</i></b>"
            else                 -> null
        }
        is StrikethroughSpan -> "</s>"
        is TypefaceSpan      -> if (span.family == "monospace") "</code>" else null
        is URLSpan           -> "</a>"
        else                 -> null
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
