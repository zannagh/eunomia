package de.zannagh.eunomia.client.toast;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One consuming mod's wording for the "this server offers no eunomia sync" notification.
 *
 * <p>Registered through {@link SyncUnavailableNotices}; a consumer never constructs one directly. Eunomia
 * ships no lang files for consumer text, so both components are resolved in the consumer's own namespace.
 *
 * @param consumerId  the registering mod's id - the registry key, and what the toast id is derived from.
 * @param title       the bold first line, or {@code null} to keep eunomia's own generic title. Most consumers
 *                    want the default: the headline states the situation, which is the same for everyone, and
 *                    only the advice underneath is mod-specific.
 * @param description the smaller second line - the consumer's own wording, the whole point of registering.
 */
public record SyncUnavailableNotice(String consumerId, @Nullable Component title, Component description) {
}
