package com.sandlotminecraft.sbt.util;

import java.util.UUID;

import world.bentobox.bentobox.api.addons.Addon;

/**
 * Emits {@code [sbt]} audit lines for state-mutating operations.
 *
 * <p>Schema per architecture.md §5.8:
 * <pre>
 *   [sbt] op=&lt;verb&gt; [target=&lt;uuid&gt;] [result=&lt;ok|fail|refused|noop&gt;] [&lt;k=v...&gt;]
 * </pre>
 *
 * <p>Two emit points:
 * <ul>
 *   <li>{@link #op(String, UUID, String...)} — intent line, written BEFORE the save call so a
 *       crash mid-save still leaves evidence.</li>
 *   <li>{@link #result(String, UUID, String, String...)} — completion line, written AFTER the
 *       save completes (or fails / is refused / is a noop).</li>
 * </ul>
 *
 * <p>Logger handle is injected via {@link #init(Addon)} at addon {@code onEnable}; OQ-B
 * resolved to use {@link Addon#log(String)} (the addon's own logger, which prefixes
 * {@code [<addon-name>]}; the {@code [sbt]} prefix remains the operator's grep target).
 */
public final class AuditLog {

    private static Addon logger;

    private AuditLog() {
        // utility
    }

    public static void init(Addon addon) {
        logger = addon;
    }

    /** Intent line. Emitted BEFORE the save call. */
    public static void op(String verb, UUID target, String... kvPairs) {
        require();
        logger.log(formatLine(verb, target, null, kvPairs));
    }

    /**
     * Completion line. Emitted AFTER the save completes (or fails / is refused / is a noop).
     *
     * @param outcome one of {@code ok}, {@code fail}, {@code refused}, {@code noop}.
     */
    public static void result(String verb, UUID target, String outcome, String... kvPairs) {
        require();
        logger.log(formatLine(verb, target, outcome, kvPairs));
    }

    static String formatLine(String verb, UUID target, String outcome, String... kvPairs) {
        if (kvPairs.length % 2 != 0) {
            throw new IllegalArgumentException("kvPairs must be even-length, got " + kvPairs.length);
        }
        StringBuilder sb = new StringBuilder("[sbt] op=").append(verb);
        if (outcome != null) {
            sb.append(' ').append("result=").append(outcome);
        }
        if (target != null) {
            sb.append(' ').append("target=").append(target);
        }
        for (int i = 0; i < kvPairs.length; i += 2) {
            sb.append(' ').append(kvPairs[i]).append('=').append(quoteIfNeeded(kvPairs[i + 1]));
        }
        return sb.toString();
    }

    private static String quoteIfNeeded(String value) {
        if (value == null) {
            return "null";
        }
        if (value.indexOf(' ') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    private static void require() {
        if (logger == null) {
            throw new IllegalStateException("AuditLog.init(addon) must be called before any emit");
        }
    }
}
