package com.dwellio.api.notice;

import java.util.regex.Pattern;

final class MarkdownSanitizer {

    private static final Pattern SCRIPT = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");

    private MarkdownSanitizer() {
    }

    /** Strip HTML/script so notice bodies stay Markdown-only. */
    static String sanitize(String markdown) {
        if (markdown == null) {
            return null;
        }
        String withoutScripts = SCRIPT.matcher(markdown).replaceAll("");
        return TAG.matcher(withoutScripts).replaceAll("").trim();
    }
}
