package de.zannagh.eunomia.networking.examples;

import de.zannagh.eunomia.configuration.ReplicatedPlayerConfig;
import de.zannagh.eunomia.configuration.ReplicatedPlayerConfigStore;
import de.zannagh.eunomia.keyed.ReplicatedClientStore;
import de.zannagh.eunomia.networking.packets.KeyedPacket;

/**
 * The example replicated store, wired identically on the loaders and Paper (server) and on the client (mirror).
 * A consumer mod follows this exact shape: declare a bidirectional {@link KeyedPacket} over a
 * {@link ReplicatedPlayerConfig} DTO, {@link ReplicatedPlayerConfigStore#enableServer() enable a store}
 * server-side, and {@link ReplicatedClientStore#enableClient() enable a mirror} client-side.
 */
public final class ExampleReplication {

    /** The bidirectional sync channel: clients send their own entry serverbound, the server relays clientbound. */
    public static final KeyedPacket<ExampleReplicatedEntry> CHANNEL =
            KeyedPacket.keyedBidirectional("eunomia", "example_replicated", ExampleReplicatedEntry.class);

    private static volatile ReplicatedPlayerConfigStore<ExampleReplicatedEntry> serverStore;

    private static volatile ReplicatedClientStore<ExampleReplicatedEntry> clientMirror;

    private ExampleReplication() {
    }

    /** Server-side: an in-memory replicated per-player store keyed by UUID, registered for push-on-join. */
    public static ReplicatedPlayerConfigStore<ExampleReplicatedEntry> enableServer() {
        if (serverStore == null) {
            serverStore = new ReplicatedPlayerConfigStore<>(
                    ExampleReplicatedEntry.class, id -> new ExampleReplicatedEntry(id, ""), CHANNEL).enableServer();
        }
        return serverStore;
    }

    /** Client-side: the mirror store, kept in sync by snapshot-on-join and per-entry relays. */
    public static ReplicatedClientStore<ExampleReplicatedEntry> enableClient() {
        if (clientMirror == null) {
            clientMirror = new ReplicatedClientStore<>(1, ExampleReplicatedEntry.class, CHANNEL).enableClient();
        }
        return clientMirror;
    }

    /** The client mirror, or {@code null} until {@link #enableClient()} runs. */
    public static ReplicatedClientStore<ExampleReplicatedEntry> clientMirror() {
        return clientMirror;
    }
}
