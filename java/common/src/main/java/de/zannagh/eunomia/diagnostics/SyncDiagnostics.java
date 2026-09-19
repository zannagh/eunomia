package de.zannagh.eunomia.diagnostics;

import org.jspecify.annotations.Nullable;

/**
 * The suppression rules behind eunomia's two sync diagnostic toasts, as pure functions over a
 * {@link ClientSyncState}.
 *
 * <p>A diagnostic toast is only worth raising when it tells the player something they can act on. Everything
 * else is nagging, and a library that nags on every join gets its toasts switched off wholesale - which then
 * also costs the consuming mod the warnings that <em>did</em> matter. Hence the two predicates below are
 * deliberately narrow, and deliberately disjoint: for any one connection at most one of them can be true, so
 * the player never gets two cards saying overlapping things.
 *
 * <p>The class holds no state and never touches Minecraft. That is the point: the client-side call sites are
 * untestable (no test source set exists for the client sources), so the judgement lives here where it can be
 * covered, and the call sites are reduced to gathering facts.
 */
public final class SyncDiagnostics {

    /** Toast-id key of eunomia's own "this server has no sync" card, and the stem every consumer's card gets. */
    public static final String MISSING_SERVER_SYNC_TOAST_KEY = "eunomia:sync_unavailable";

    private SyncDiagnostics() {
    }

    /**
     * Whether the "no server-side sync" card should carry eunomia's own generic copy rather than a consuming
     * mod's wording.
     *
     * <p>Only when nobody has registered any. A consumer that has said what it wants the player to read has
     * said it better than the library can - eunomia's copy can only talk about eunomia, which is not the mod
     * the player installed - so once even one registration exists the generic card is <em>replaced</em>, not
     * added to. Otherwise a single consumer would produce two cards saying the same thing.</p>
     *
     * @param registeredConsumers how many consuming mods have supplied their own wording.
     * @return {@code true} when eunomia should raise its own card instead.
     */
    public static boolean useGenericMissingSyncNotice(int registeredConsumers) {
        return registeredConsumers <= 0;
    }

    /**
     * The toast-id key a "no server-side sync" card is raised under.
     *
     * <p>Derived from the consumer id so that N registered mods produce N distinct notifications rather than
     * N attempts to be the same one - toasts sharing an id can replace each other, and a player told about
     * three unsynchronised mods should see three cards.</p>
     *
     * @param consumerId the registering mod's id, or {@code null} for eunomia's own generic card.
     * @return the namespaced toast-id key.
     */
    public static String missingSyncToastKey(@Nullable String consumerId) {
        if (consumerId == null || consumerId.isBlank()) {
            return MISSING_SERVER_SYNC_TOAST_KEY;
        }
        return MISSING_SERVER_SYNC_TOAST_KEY + "/" + consumerId.trim();
    }

    /**
     * Whether to tell the player that this server offers no eunomia sync at all and that cloud sync is the way
     * to get some ("toast 1"). True only when every one of these holds:
     *
     * <ul>
     *   <li>the capability probe has <em>resolved</em> - an in-flight probe is not yet bad news;</li>
     *   <li>the joined server did <em>not</em> answer the handshake - if it did, sync works and there is
     *       nothing to report;</li>
     *   <li>this is a real multiplayer connection - in singleplayer and on LAN worlds there is no remote party
     *       to sync with, so pointing at a cloud-sync setting would be noise;</li>
     *   <li>the relay is <em>not</em> already configured - the whole message is "go enable cloud sync", which is
     *       insulting to a player who already did. If that configured relay then turns out to be unreachable,
     *       {@link #shouldWarnRelayUnreachable(ClientSyncState)} covers it with accurate copy instead.</li>
     * </ul>
     *
     * @param state the facts about this connection.
     * @return {@code true} when the "no server-side sync" toast should be raised.
     */
    public static boolean shouldWarnMissingServerSync(ClientSyncState state) {
        if (!state.capabilitiesResolved() || state.serverSpeaksEunomia()) {
            return false;
        }
        if (!state.remoteServer()) {
            return false;
        }
        return !state.relayUsable();
    }

    /**
     * Whether to tell the player that their configured relay could not be reached ("toast 2"). True only when:
     *
     * <ul>
     *   <li>a relay is actually configured - there is nothing to be unreachable otherwise;</li>
     *   <li>this is a real multiplayer connection - the relay is scoped per server address, so it is never
     *       consulted in singleplayer or on LAN;</li>
     *   <li>the joined Minecraft server does <em>not</em> speak eunomia. When it does (the
     *       {@code preferExternalTransport} case), losing the relay is a demotion, not an outage: the client
     *       lands back on a perfectly working in-game transport and sync still happens. Saying "no server
     *       synchronisation will happen" there would simply be false.</li>
     * </ul>
     *
     * <p>The caller supplies the "unreachable" fact by only asking at all on a failed probe; it is not part of
     * the state because it is not something that can be sampled - it is the outcome of one specific attempt.
     *
     * @param state the facts about this connection.
     * @return {@code true} when the "relay unreachable" toast should be raised.
     */
    public static boolean shouldWarnRelayUnreachable(ClientSyncState state) {
        if (!state.relayUsable() || !state.remoteServer()) {
            return false;
        }
        return !state.serverSpeaksEunomia();
    }
}
