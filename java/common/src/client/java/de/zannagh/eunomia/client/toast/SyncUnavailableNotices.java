package de.zannagh.eunomia.client.toast;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a consuming mod says what the "this server offers no eunomia sync" notification should tell the
 * player - for instance "Armor Hider can synchronise via Cloud. Go to Settings to enable it."
 *
 * <p>Eunomia's own copy can only speak about eunomia, which is a library the player likely does not know they
 * have. The mod they <em>did</em> install is the one whose data is not being synchronised, so it is the one
 * that can phrase the message usefully.
 *
 * <p><b>A registry, not a single slot.</b> Two consumers embedding eunomia in the same pack would fight over
 * one settable field and the winner would be whichever initialised last - mod load order, which is not
 * something either author controls or can test against. Instead each registration produces its own card:
 * every mod that registered says its piece, and nothing is silently dropped.
 *
 * <p><b>What gets raised.</b> With no registration, eunomia raises its own generic card as it always has.
 * With one or more, it raises exactly one card per registration and <em>not</em> its generic one - the
 * generic card exists only for the case where nobody has said anything better.
 *
 * <p>Cards are ordered by consumer id rather than by registration order, so the same set of mods produces the
 * same screen every launch regardless of the order they initialised in.
 *
 * <p>Registration is idempotent: re-registering an id replaces that entry, so a consumer may call it from an
 * initializer that runs more than once without accumulating duplicates. Every method is safe from any thread.
 *
 * <p>This lives in the client source set, not next to {@code EunomiaClientOptions}, because a registration
 * carries {@link Component}s - a {@code net.minecraft.client.*} type that the common source set may not so
 * much as name. A consumer therefore registers from its <em>client</em> initializer; the global toast kill
 * switch remains reachable from a common one, as before.
 *
 * @since 0.3.3
 */
public final class SyncUnavailableNotices {

    private static final Map<String, SyncUnavailableNotice> REGISTERED = new ConcurrentHashMap<>();

    private SyncUnavailableNotices() {
    }

    /**
     * Registers this mod's wording, keeping eunomia's generic title.
     *
     * @param consumerId  your mod id. Also the registry key: registering twice under the same id replaces the
     *                    earlier entry rather than adding a second card.
     * @param description the line the player reads. Resolve it in your own namespace - eunomia ships no lang
     *                    files for consumer text.
     */
    public static void register(String consumerId, Component description) {
        register(consumerId, null, description);
    }

    /**
     * Registers this mod's wording, overriding the title as well.
     *
     * @param consumerId  your mod id; see {@link #register(String, Component)}.
     * @param title       the bold first line, or {@code null} for eunomia's generic one.
     * @param description the line the player reads.
     */
    public static void register(String consumerId, @Nullable Component title, Component description) {
        String key = requireId(consumerId);
        Objects.requireNonNull(description, "description");
        REGISTERED.put(key, new SyncUnavailableNotice(key, title, description));
    }

    /**
     * Drops a registration again, so this mod stops contributing a card.
     *
     * @param consumerId the id it was registered under.
     * @return {@code true} when something was actually removed.
     */
    public static boolean unregister(String consumerId) {
        return consumerId != null && REGISTERED.remove(consumerId.trim()) != null;
    }

    /** Drops every registration, restoring eunomia's generic card. Tests, and a consumer wanting a clean slate. */
    public static void clear() {
        REGISTERED.clear();
    }

    /**
     * Every registration, ordered by consumer id. A snapshot: iterating it is unaffected by a registration
     * happening on another thread halfway through.
     *
     * @return the registered notices, empty when eunomia should raise its own generic card instead.
     */
    public static List<SyncUnavailableNotice> registered() {
        List<SyncUnavailableNotice> notices = new ArrayList<>(REGISTERED.values());
        notices.sort(Comparator.comparing(SyncUnavailableNotice::consumerId));
        return notices;
    }

    private static String requireId(String consumerId) {
        if (consumerId == null || consumerId.isBlank()) {
            throw new IllegalArgumentException("Consumer id must not be blank");
        }
        return consumerId.trim();
    }
}
