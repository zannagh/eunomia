package de.zannagh.eunomia.client.gui.screens;

import de.zannagh.eunomia.client.settings.ServerSettingsView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * The one-line readout that tells the player what just happened to the server section, and the only
 * place a {@code ServerSettingsStatus} is turned into words.
 *
 * <p>It is a permanently disabled {@link Button} rather than a text widget, which is the same trick
 * the reset buttons in {@link EunomiaSettingsOptions} use: a disabled button still renders its tooltip
 * on hover, so the short label can stay short while the server's own - unlocalised, sometimes long -
 * refusal reason lives in the tooltip where it does not have to fit in half a row.
 *
 * <p>Every state the exchange can be in has an entry here, including both waiting states. There is
 * deliberately no "no status yet": the section is built showing {@link #pending()}, and the client-side
 * timeout guarantees an answer replaces it within five seconds, so the screen can never sit on an
 * indefinite spinner even against a server that has never heard of eunomia.
 */
final class ServerSettingsStatusLine {

    private static final String KEY = "eunomia.settings.server.status.";

    private final Button widget;

    ServerSettingsStatusLine() {
        widget = Button.builder(Component.empty(), press -> { })
                .bounds(0, 0, EunomiaSettingsOptions.CONTROL_WIDTH, EunomiaSettingsOptions.CONTROL_HEIGHT)
                .build();
        widget.active = false;
    }

    Button widget() {
        return widget;
    }

    /** Waiting for the answer to a read. */
    void pending() {
        show("pending", ChatFormatting.GRAY, null);
    }

    /** Waiting for the answer to a write. */
    void saving() {
        show("saving", ChatFormatting.GRAY, null);
    }

    /**
     * Reports how an exchange concluded. The two {@code OK} cases are split, because "these are the
     * server's settings and you may change them" and "these are the server's settings and you may only
     * look" are the difference between an admin screen and a read-only one, and the player has to be
     * told which they are looking at rather than inferring it from greyed-out widgets.
     */
    void show(ServerSettingsView view) {
        switch (view.status()) {
            case OK -> {
                if (view.editable()) {
                    show("admin", ChatFormatting.GREEN, null);
                } else {
                    show("readonly", ChatFormatting.GRAY, null);
                }
            }
            case APPLIED -> show("applied", ChatFormatting.GREEN, null);
            case DENIED -> show("denied", ChatFormatting.RED, view.detail());
            case INVALID_ADDRESS -> show("invalidAddress", ChatFormatting.RED, view.detail());
            case UNAVAILABLE -> show("unavailable", ChatFormatting.YELLOW, null);
            default -> show("unavailable", ChatFormatting.YELLOW, null);
        }
    }

    private void show(String state, ChatFormatting colour, @Nullable String detail) {
        widget.setMessage(Component.translatable(KEY + state).withStyle(colour));
        MutableComponent tooltip = Component.translatable(KEY + state + ".tooltip");
        if (detail != null && !detail.isBlank()) {
            // Straight from the server, so unlocalised on purpose: inventing a translation key per
            // refusal reason would mean a server could only ever say things this client already knows.
            tooltip.append("\n\n").append(Component.literal(detail).withStyle(ChatFormatting.GRAY));
        }
        widget.setTooltip(Tooltip.create(tooltip));
    }
}
