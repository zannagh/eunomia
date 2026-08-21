package de.zannagh.eunomia.clients;

import com.google.gson.JsonElement;

/**
 * The JSON envelope exchanged with the external relay (the C# server), over both the REST send path and the
 * WebSocket receive path. Field names and shape match the server's {@code PacketEnvelope} exactly.
 * <p>
 * {@link #scope} partitions all data by the Minecraft server the client is connected to, so two clients on
 * different servers never see each other's data. {@link #name} carries the human-readable server label (the
 * player's entry for that server) purely for display on the server side; it never affects partitioning.
 * {@link #key} is the {@code KeyPath} (slash-joined) for keyed packets and {@code null} for plain ones;
 * {@link #replicated} marks a {@code Replicated} payload the server must store and push to newcomers.
 */
public class PacketEnvelope {

    public String scope;

    public String name;

    public String channel;

    public String key;

    public boolean replicated;

    public String sender;

    public JsonElement payload;

    public PacketEnvelope() {
    }

    public PacketEnvelope(String scope, String name, String channel, String key, boolean replicated, String sender, JsonElement payload) {
        this.scope = scope;
        this.name = name;
        this.channel = channel;
        this.key = key;
        this.replicated = replicated;
        this.sender = sender;
        this.payload = payload;
    }
}
