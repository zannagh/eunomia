package de.zannagh.eunomia.armorhider;

import com.google.gson.JsonObject;
import de.zannagh.eunomia.common.SemanticVersion;
import de.zannagh.eunomia.configuration.PlayerLinkedConfigurationItemBase;
import de.zannagh.eunomia.configuration.ReplicatedPlayerConfig;

/**
 * A stand-in for Armor Hider's real per-player config, used only to prove eunomia storage round-trips its
 * on-wire JSON shape losslessly (see {@code ArmorHiderCompatibilityTest}). Every top-level scalar field of
 * the real config is declared here so the flat part of the document round-trips field-for-field; the three
 * deeply-nested blocks ({@code exclusionItems}, {@code globalPlayerOverride}, {@code individualConfigurations})
 * are kept as raw {@link JsonObject}s instead of being modeled key-by-key - Gson (de)serializes a
 * {@code JsonObject} natively, so nothing inside them is dropped without having to mirror every nested key.
 */
public final class ArmorHiderConfig extends PlayerLinkedConfigurationItemBase<ArmorHiderConfig>
        implements ReplicatedPlayerConfig<ArmorHiderConfig> {

    private static final SemanticVersion VERSION = new SemanticVersion(1, 0, 0, null);

    public int configVersion;

    public double helmetOpacity;
    public double chestOpacity;
    public double legsOpacity;
    public double bootsOpacity;
    public boolean helmetGlint;
    public boolean chestGlint;
    public boolean legsGlint;
    public boolean bootsGlint;
    public boolean enableCombatDetection;
    public boolean opacityAffectingElytra;
    public double elytraOpacity;
    public boolean elytraInFlight;
    public boolean elytraGlint;
    public boolean opacityAffectingHatOrSkull;
    public boolean affectAccessories;
    public boolean affectHeadAccessory;
    public boolean affectChestAccessory;
    public boolean affectLegsAccessory;
    public boolean affectFeetAccessory;
    public boolean disableArmorHider;
    public boolean disableArmorHiderForOthers;
    public boolean usePlayerSettingsWhenUndeterminable;
    public double offHandOpacity;

    public JsonObject exclusionItems;

    public boolean showSettingsInSkinCustomization;
    public String settingsScreenLocation;
    public boolean inCombatUseDefaultModel;
    public String hiddenModelBehaviour;
    public boolean showShieldWhenBlocking;
    public boolean disableArmorHiderOnInvisibility;

    public JsonObject individualConfigurations;

    public boolean useGlobalOverrideForAllPlayers;

    public JsonObject globalPlayerOverride;

    public int irisDitheringScale;
    public int irisDitherPhases;
    public int irisDitherResCap;
    public String irisPartialTransparencyMode;

    public String playerName;

    public ArmorHiderConfig() {
    }

    @Override
    public ArmorHiderConfig getValue() {
        return this;
    }

    @Override
    public void setValue(ArmorHiderConfig newValue) {
        throw new UnsupportedOperationException("field-by-field copy is not needed for this compatibility test");
    }

    @Override
    public ArmorHiderConfig getDefaultValue() {
        return new ArmorHiderConfig();
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
    public ArmorHiderConfig migrateFrom(ArmorHiderConfig old) {
        return old;
    }
}
