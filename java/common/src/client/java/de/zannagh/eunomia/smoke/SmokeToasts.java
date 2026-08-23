//? if fcgt {
package de.zannagh.eunomia.smoke;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

// Reads vanilla's toast queue, so a toast raised through eunomia's API can be asserted rather than
// merely photographed.
//
// The accessor moved in 26.2-snapshot-3: below that boundary the manager hangs off Minecraft, from
// there on it hangs off Gui, which is why this mirrors ToastDispatcher's own in-source conditional
// exactly. The type name itself did not move, so only the expression is gated.
//
// Counting is done over the manager's own List (visible) and Deque (queued) fields rather than
// through a public accessor because there is none: freeSlotCount() is private on every version in
// range. The field types are matched, not the field names, so a rename upstream cannot silently turn
// this into a check that always passes. The Set field (played toast sounds) is deliberately excluded
// - it grows independently of how many toasts are on screen.
public final class SmokeToasts {

    private SmokeToasts() {
    }

    public static ToastManager manager(Minecraft client) {
        //? if >= 26.2-0.snapshot.3 {
        return client.gui == null ? null : client.gui.toastManager();
        //?} else {
        /*return client.getToastManager();
        *///?}
    }

    // How many toasts vanilla currently holds, visible plus queued. -1 when there is no manager yet.
    public static int count(ClientGameTestContext context) {
        return context.computeOnClient(client -> countOn(manager(client)));
    }

    // Drops everything on screen, so one assertion cannot inherit the previous one's leftovers.
    public static void clear(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ToastManager manager = manager(client);
            if (manager != null) {
                manager.clear();
            }
        });
        context.waitTicks(2);
    }

    // Waits for the queue to reach an exact size and then for the slide-in animation to finish, so a
    // screenshot taken straight afterwards shows the toast on screen rather than still off the right
    // edge. Vanilla toasts live about 100 ticks, so this is well inside their lifetime.
    public static void waitUntilShown(ClientGameTestContext context, int expected, int timeoutTicks) {
        waitForCount(context, expected, timeoutTicks);
        context.waitTicks(15);
    }

    // Waits for the queue to reach an exact size, and reports what it actually saw when it does not.
    public static void waitForCount(ClientGameTestContext context, int expected, int timeoutTicks) {
        try {
            context.waitFor(client -> countOn(manager(client)) == expected, timeoutTicks);
        } catch (AssertionError | RuntimeException e) {
            throw new AssertionError("Toast queue never reached " + expected
                    + " within " + timeoutTicks + " ticks; it holds " + count(context), e);
        }
    }

    private static int countOn(ToastManager manager) {
        if (manager == null) {
            return -1;
        }
        int total = 0;
        for (Field field : manager.getClass().getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType()) && !Deque.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(manager);
                if (value instanceof Collection<?> collection) {
                    total += collection.size();
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // A field we cannot read simply does not contribute; the assertion below still needs
                // at least one readable one to move, so an unreadable pair fails loudly on its own.
                continue;
            }
        }
        return total;
    }
}
//?}
