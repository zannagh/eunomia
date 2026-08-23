package de.zannagh.eunomia.diagnostics;

/**
 * An immutable snapshot of everything the sync diagnostics need to decide whether a warning is worth showing.
 *
 * <p>It exists so the decision itself never touches a running client. The facts are gathered at the call site
 * (which does live on the client and does read {@code Minecraft}), frozen into this record, and only then
 * handed to {@link SyncDiagnostics}. That keeps the interesting half - the suppression rules - unit-testable
 * even though there is no test source set for the client sources.
 *
 * <p>Every field is a fact about <em>one</em> connection attempt. Nothing here is cached across joins; the
 * capability probe resolves once per join and the snapshot is rebuilt each time.
 *
 * @param capabilitiesResolved whether the capability probe has concluded either way. An unresolved probe means
 *                             "we do not know yet", which is never a reason to warn the player.
 * @param serverSpeaksEunomia  whether the joined Minecraft server answered the handshake. Only meaningful once
 *                             {@code capabilitiesResolved}.
 * @param remoteServer         whether this is a real multiplayer connection (a server entry with an address).
 *                             {@code false} in singleplayer and on LAN worlds, where there is no remote party to
 *                             synchronise with and no relay scope either.
 * @param relayUsable          whether the effective settings describe a usable relay destination - the external
 *                             fallback is enabled and an address is configured
 *                             ({@code EunomiaSyncSettings.externalRelayUsable()}). Note this says "configured",
 *                             not "reachable"; reachability is a separate, later fact.
 */
public record ClientSyncState(
        boolean capabilitiesResolved,
        boolean serverSpeaksEunomia,
        boolean remoteServer,
        boolean relayUsable) {
}
