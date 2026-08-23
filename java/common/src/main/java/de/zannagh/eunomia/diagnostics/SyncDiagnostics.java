package de.zannagh.eunomia.diagnostics;

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

    private SyncDiagnostics() {
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
