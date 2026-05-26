package com.enterprise.techdebt.logging;

import java.util.regex.Pattern;

/**
 * Masks tokens and obvious source-code content from arbitrary log strings.
 *
 * Policy enforced here:
 *  - Authorization headers (Bearer, Basic) → keep first 6 chars then mask
 *  - Veracode HMAC sig= → mask the signature
 *  - sk-... OpenAI-style keys → mask
 *  - Long Base64-ish tokens → mask
 *  - Unified-diff hunks (lines starting with + or - inside ``` blocks) → replaced by length
 *
 * The scrubber is conservative: it errs on the side of false positives. Any pattern
 * uncertain to be a secret is masked. Add new patterns as they're seen in incidents.
 */
public final class LogScrubber {

    private static final Pattern BEARER = Pattern.compile(
            "(?i)(Bearer|Basic)\\s+([A-Za-z0-9._\\-+/=]+)");
    private static final Pattern VERACODE_SIG = Pattern.compile(
            "(VERACODE-HMAC-SHA-256\\s+[^,]*,ts=\\d+,nonce=[0-9a-fA-F]+,sig=)([0-9a-fA-F]+)");
    private static final Pattern OPENAI_KEY = Pattern.compile(
            "(?i)sk-[A-Za-z0-9_\\-]{20,}");
    private static final Pattern LONG_TOKEN = Pattern.compile(
            "([A-Za-z0-9_\\-]{40,})");
    private static final Pattern PASSWORD_KV = Pattern.compile(
            "(?i)(password|secret|api[_-]?key|token)\\s*[:=]\\s*\"?([^\\s\",;]+)");
    // Match lines that start with a single + or - (diff-content lines), but skip
    // `+++ a/file` / `--- b/file` file-marker lines (second char also + or -).
    private static final Pattern DIFF_HUNK = Pattern.compile(
            "(?m)^[+\\-](?![+\\-]).*$");

    private LogScrubber() {}

    public static String scrub(String message) {
        if (message == null || message.isEmpty()) return message;
        String out = message;
        out = BEARER.matcher(out).replaceAll(m -> m.group(1) + " " + mask(m.group(2)));
        out = VERACODE_SIG.matcher(out).replaceAll(m -> m.group(1) + mask(m.group(2)));
        out = OPENAI_KEY.matcher(out).replaceAll(m -> mask(m.group()));
        out = PASSWORD_KV.matcher(out).replaceAll(m -> m.group(1) + "=***");
        // Diff-hunk redaction is intentional and verbose — replaces each touched line
        // with a marker that preserves the line count so reviewers can still see scale.
        out = DIFF_HUNK.matcher(out).replaceAll("«source-line redacted»");
        // Long-token mask runs last to avoid double-masking inside Bearer matches.
        out = LONG_TOKEN.matcher(out).replaceAll(m -> {
            String s = m.group();
            // Don't mask things we've already masked or obvious non-secrets.
            if (s.startsWith("***") || s.equals("«source-line")) return s;
            return mask(s);
        });
        return out;
    }

    private static String mask(String s) {
        if (s == null) return null;
        if (s.length() <= 6) return "***";
        return s.substring(0, 6) + "***";
    }
}
