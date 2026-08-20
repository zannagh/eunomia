package de.zannagh.eunomia.networking.examples;

import de.zannagh.eunomia.common.SemanticVersion;
import de.zannagh.eunomia.configuration.PlayerLinkedConfigurationItemBase;
import de.zannagh.eunomia.configuration.ReplicatedPlayerConfig;

import java.util.UUID;

/**
 * A minimal {@link ReplicatedPlayerConfig} for the example wiring: one note per player, keyed by the player's UUID
 * (via the default {@link ReplicatedPlayerConfig#keyPath()}). It exercises the real player-config path end to end -
 * a client updates its own note, the server stores it under the authenticated sender, relays it to everyone else,
 * and dumps the full set to each newcomer on join.
 */
public class ExampleReplicatedEntry extends PlayerLinkedConfigurationItemBase<ExampleReplicatedEntry>
        implements ReplicatedPlayerConfig<ExampleReplicatedEntry> {

    private static final SemanticVersion VERSION = new SemanticVersion(1, 0, 0, null);

    public String note;

    public ExampleReplicatedEntry() {
    }

    public ExampleReplicatedEntry(UUID player, String note) {
        super(player);
        this.note = note;
    }

    @Override
    public ExampleReplicatedEntry getValue() {
        return this;
    }

    @Override
    public void setValue(ExampleReplicatedEntry newValue) {
        this.note = newValue.note;
        setPlayerId(newValue.getPlayerId());
    }

    @Override
    public ExampleReplicatedEntry getDefaultValue() {
        return new ExampleReplicatedEntry();
    }

    @Override
    public SemanticVersion getSchemaVersion() {
        return VERSION;
    }

    @Override
    public SemanticVersion getCurrentSchemaVersion() {
        return VERSION;
    }

    @Override
    public ExampleReplicatedEntry migrateFrom(ExampleReplicatedEntry old) {
        return old;
    }
}
