package de.zannagh.eunomia.keyed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An ordered, immutable sequence of string key segments - the composite primary key of a
 * {@link KeyedStore}. A single-segment path ({@code KeyPath.of(uuid)}) is the ordinary "keyed by id"
 * case; a longer path ({@code KeyPath.of(uuid, "armor", "boots")}) walks a tree, exactly the way a
 * table's multi-column primary key addresses a row.
 * <p>
 * Segments are always strings because that is what a JSON object key is: a store persists to nested
 * JSON objects keyed by these segments, so {@code KeyPath.of(uuid, "armor")} round-trips as
 * {@code { "<uuid>": { "armor": ... } }}. Non-string segments (a {@link java.util.UUID}, an enum) are
 * accepted and folded to their {@code toString()} - the caller converts back at the store boundary.
 *
 * @since 0.1.0
 */
public final class KeyPath implements Comparable<KeyPath> {

    private static final KeyPath ROOT = new KeyPath(List.of());

    private final List<String> segments;

    private KeyPath(List<String> segments) {
        this.segments = segments;
    }

    /** The empty path (zero segments), the root of every tree. */
    public static KeyPath root() {
        return ROOT;
    }

    /** A path over the given segments, each folded to its {@code toString()}. None may be null or empty. */
    public static KeyPath of(Object... segments) {
        Objects.requireNonNull(segments, "segments");
        List<String> out = new ArrayList<>(segments.length);
        for (Object segment : segments) {
            out.add(requireSegment(segment));
        }
        return out.isEmpty() ? ROOT : new KeyPath(List.copyOf(out));
    }

    /** A path over the given string segments. None may be null or empty. */
    public static KeyPath ofSegments(List<String> segments) {
        Objects.requireNonNull(segments, "segments");
        List<String> out = new ArrayList<>(segments.size());
        for (String segment : segments) {
            out.add(requireSegment(segment));
        }
        return out.isEmpty() ? ROOT : new KeyPath(List.copyOf(out));
    }

    /** The number of segments in this path. */
    public int length() {
        return segments.size();
    }

    /** Whether this is the empty root path. */
    public boolean isRoot() {
        return segments.isEmpty();
    }

    /** The segment at {@code index} (0-based). */
    public String segment(int index) {
        return segments.get(index);
    }

    /** The last segment - the leaf key under this path's parent. */
    public String last() {
        if (segments.isEmpty()) {
            throw new IllegalStateException("root path has no last segment");
        }
        return segments.get(segments.size() - 1);
    }

    /** The segments as an immutable list. */
    public List<String> segments() {
        return segments;
    }

    /** This path with {@code segment} appended - descends one level into the tree. */
    public KeyPath child(Object segment) {
        List<String> next = new ArrayList<>(segments);
        next.add(requireSegment(segment));
        return new KeyPath(List.copyOf(next));
    }

    /** This path without its last segment - ascends one level. */
    public KeyPath parent() {
        if (segments.isEmpty()) {
            throw new IllegalStateException("root path has no parent");
        }
        return new KeyPath(List.copyOf(segments.subList(0, segments.size() - 1)));
    }

    /** Whether this path begins with all of {@code prefix}'s segments (a prefix is a subtree root). */
    public boolean startsWith(KeyPath prefix) {
        if (prefix.segments.size() > segments.size()) {
            return false;
        }
        return segments.subList(0, prefix.segments.size()).equals(prefix.segments);
    }

    private static String requireSegment(Object segment) {
        Objects.requireNonNull(segment, "key segment");
        String value = segment.toString();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("key segment must not be empty");
        }
        return value;
    }

    @Override
    public int compareTo(KeyPath other) {
        int shared = Math.min(segments.size(), other.segments.size());
        for (int i = 0; i < shared; i++) {
            int cmp = segments.get(i).compareTo(other.segments.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(segments.size(), other.segments.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof KeyPath other && segments.equals(other.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        return String.join("/", segments);
    }
}
