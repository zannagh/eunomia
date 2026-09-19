package de.zannagh.eunomia.configuration;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The shared bookkeeping behind {@link EunomiaConfig}'s "have I already told the player about this?" lists.
 *
 * <p>Two such lists exist - the relay hosts data has been sent through, and the servers whose missing
 * server-side sync has already been announced - and both want the same three operations with the same
 * normalisation. Keeping the logic here rather than duplicated on the config means a change to how values are
 * compared cannot end up applying to one list and not the other, and keeps {@link EunomiaConfig} itself from
 * growing a second near-identical trio of methods.
 *
 * <p>Values are compared case-insensitively and trimmed. Both lists hold things a user typed or a server sent
 * (host names, server addresses), where case is not identity; without this a single capitalisation difference
 * would re-announce something the player has already seen.
 *
 * <p>Callers hold the list; this class never allocates one. That keeps the nullable-until-first-use shape of
 * the persisted fields - and therefore the JSON documents - entirely the config's business.
 */
final class SeenValues {

    private SeenValues() {
    }

    /** Normalises a raw value to its comparison form, or {@code null} when it carries no information. */
    static @Nullable String normalize(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Whether {@code raw} has already been recorded in {@code values}. */
    static boolean contains(@Nullable List<String> values, @Nullable String raw) {
        String normalized = normalize(raw);
        return normalized != null && values != null && values.contains(normalized);
    }

    /**
     * Appends {@code raw} to {@code values} unless it is already there, evicting the oldest entries until at
     * most {@code cap} remain.
     *
     * <p>Eviction is oldest-first and deliberate: these lists exist only to suppress a repeat notification, so
     * the worst an eviction can cost is one extra card for a value the player has not touched in a very long
     * time. An unbounded list, by contrast, grows for the lifetime of the install.
     *
     * @param values the list to record into; the caller has already created it.
     * @param raw    the value in any case; {@code null} and blank are ignored.
     * @param cap    the maximum number of entries to keep; must be positive.
     * @return {@code true} when this call was the first to record {@code raw}.
     */
    static boolean remember(List<String> values, @Nullable String raw, int cap) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return false;
        }
        if (values.contains(normalized)) {
            return false;
        }
        values.add(normalized);
        while (values.size() > cap) {
            values.remove(0);
        }
        return true;
    }
}
