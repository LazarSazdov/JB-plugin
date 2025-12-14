package com.hackathon.util;

/** Utility to ensure no code blocks are displayed. */
public final class HtmlSanitizer {
    private HtmlSanitizer() {}

    /**
     * Strip code-like content from HTML or markdown-ish strings.
     * Removes <pre>...</pre>, <code>...</code>, and fenced code blocks ```...```.
     */
    public static String stripCodeBlocks(String html) {
        if (html == null) return null;
        String out = html;
        // Remove fenced code blocks (``` ... ```)
        out = out.replaceAll("(?is)```.*?```", "");
        // Remove <pre> blocks
        out = out.replaceAll("(?is)<pre[^>]*>.*?</pre>", "");
        // Remove inline and block <code>
        out = out.replaceAll("(?is)<code[^>]*>.*?</code>", "");
        // Remove leftover empty paragraphs/spaces
        out = out.replaceAll("(?is)(<p>\\s*</p>)", "");
        out = out.replaceAll("\n{3,}", "\n\n");
        return out.trim();
    }
}
