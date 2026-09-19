package de.zannagh.eunomia.client.gui.screens;

import de.zannagh.eunomia.client.settings.SyncSettingSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Every string the player's own settings rows put on screen, composed in one place.
 *
 * <p>Split out of {@link EunomiaSettingsOptions} because the two answer different questions and change
 * for different reasons: that class owns which widgets exist and what state they are in, this one owns
 * how a row explains itself. Keeping them together had the widget lifecycle - which is subtle, see that
 * class's note on rebuilds - sharing a file with a pile of translation-key plumbing.</p>
 *
 * <p>The lock-aware variants exist because a greyed-out control with no explanation is indistinguishable
 * from a broken screen. A tooltip still renders on an inactive widget, so it is the only place the
 * reason can go.</p>
 */
final class SettingsRowText {

    private SettingsRowText() {
    }

    /** The ON/OFF text of a boolean row. */
    static Component onOff(boolean value) {
        return Component.translatable(value ? "eunomia.settings.on" : "eunomia.settings.off");
    }

    /** "Currently: &lt;value&gt; (&lt;where it comes from&gt;)" - the row's provenance readout. */
    static MutableComponent describe(Component value, SyncSettingSource source) {
        return Component.translatable("eunomia.settings.effective", value, source.label())
                .withStyle(ChatFormatting.GRAY);
    }

    /** The setting's tooltip with its effective-value line, for a row nobody is enforcing. */
    static MutableComponent tooltipFor(String settingKey, Component effective) {
        return tooltipFor(settingKey, effective, false);
    }

    /** The same, with the "this server decides it" paragraph appended while the row is locked. */
    static MutableComponent tooltipFor(String settingKey, Component effective, boolean locked) {
        MutableComponent tooltip = Component.translatable(settingKey + ".tooltip")
                .append("\n\n").append(effective);
        if (locked) {
            tooltip.append("\n\n").append(Component.translatable("eunomia.settings.locked")
                    .withStyle(ChatFormatting.GOLD));
        }
        return tooltip;
    }

    /**
     * The address tooltip, plus - while the typed text would be stored as something other than itself -
     * a line naming the exact string that will be saved. That line is the screen's answer to "a bare
     * host becomes what, exactly": it shows the {@code http://} that {@code RelayAddresses} will add,
     * rather than leaving the player to assume a scheme.
     */
    static MutableComponent addressTooltip(String typed, String effective, SyncSettingSource source,
                                           boolean locked) {
        MutableComponent tooltip = tooltipFor("eunomia.settings.cloudSyncServer",
                describe(Component.literal(effective), source), locked);
        String trimmed = typed.trim();
        if (!trimmed.isEmpty() && RelayAddress.isValid(trimmed)) {
            String normalized = RelayAddress.normalize(trimmed);
            if (!normalized.equals(trimmed)) {
                tooltip.append("\n\n").append(Component
                        .translatable("eunomia.settings.cloudSyncServer.normalized", normalized)
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        return tooltip;
    }

    /**
     * Which of the three reset-button explanations a row needs. "Nothing to reset" is true of an
     * inherited row and false of a locked one - a locked row may well have an override sitting behind
     * it, it just does not apply here - so the two cannot share a string.
     */
    static String resetTooltipKey(boolean overridden, boolean locked) {
        if (locked) {
            return "eunomia.settings.reset.tooltip.locked";
        }
        return overridden ? "eunomia.settings.reset.tooltip" : "eunomia.settings.reset.tooltip.inherited";
    }
}
