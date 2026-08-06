package de.zannagh.eunomia.configuration;

import de.zannagh.eunomia.common.SemanticVersion;

//? if >= 1.20.5 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? }

import java.util.UUID;

/**
 * A minimal player-linked config for exercising {@link ServerSidePlayerConfigStorage}. It carries one real
 * data field ({@link #level}) so a JSON round-trip has something to prove, and stubs the parts of the
 * {@link ConfigurationItem} contract the store never invokes. The codec/payload members exist only on
 * {@code >= 1.20.5} (where {@link ConfigurationItem} extends {@code CustomPacketPayload}), guarded the same
 * way as the interface, so the fixture compiles as a plain class on 1.20.1 too.
 */
public class TestPlayerConfig extends PlayerLinkedConfigurationItemBase<TestPlayerConfig> {

    private static final SemanticVersion VERSION = new SemanticVersion(1, 0, 0, null);

    public int level;

    public TestPlayerConfig() {
    }

    public TestPlayerConfig(UUID playerId, int level) {
        super(playerId);
        this.level = level;
    }

    @Override
    public TestPlayerConfig getValue() {
        return this;
    }

    @Override
    public void setValue(TestPlayerConfig newValue) {
        this.level = newValue.level;
        setPlayerId(newValue.getPlayerId());
    }

    @Override
    public TestPlayerConfig getDefaultValue() {
        return new TestPlayerConfig();
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
    public TestPlayerConfig migrateFrom(TestPlayerConfig old) {
        return old;
    }

    //? if >= 1.20.5 {
    @Override
    public StreamCodec<ByteBuf, TestPlayerConfig> getCodec() {
        return null;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return null;
    }
    //? }
}
