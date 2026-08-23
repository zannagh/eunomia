//? if fcgt {
package de.zannagh.eunomia.smoke;

import de.zannagh.eunomia.Eunomia;
import de.zannagh.eunomia.client.ui.ScreenAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

// Widget-level inspection of a live screen, which is the difference between "the screenshot looks
// right" and "the widget is really there and really clickable".
//
// Reaching the widgets is less trivial than it sounds. `ScreenAccessor` exposes the screen's own two
// lists, but an options screen keeps almost everything one or two levels down: the rows live inside
// an OptionsList, whose entries are not widgets themselves, and on 26.x an entry's children are
// record wrappers around the widget rather than the widget. Rather than encode that shape - which
// has changed twice inside the supported range - the walk is generic: it follows collections,
// ContainerEventHandler children and, as a last resort, any reference field of any object that
// belongs to Minecraft's gui package or to eunomia. That is version-proof by construction.
public final class SmokeUi {

    // Enough to cover an options screen many times over; a guard against a cyclic graph eating the
    // gametest timeout rather than a real limit.
    private static final int NODE_BUDGET = 8000;

    private SmokeUi() {
    }

    // The screen the client is showing right now.
    public static Screen currentScreen(Minecraft client) {
        //? if >= 26.2-0.snapshot.3 {
        return client.gui == null ? null : client.gui.screen();
        //?} else {
        /*return client.screen;
        *///?}
    }

    // Every widget reachable from the currently open screen, in breadth-first order.
    public static List<AbstractWidget> widgets(ClientGameTestContext context) {
        return context.computeOnClient(client -> collect(currentScreen(client)));
    }

    // The rendered label of every reachable widget - the text a player actually reads.
    public static List<String> labels(ClientGameTestContext context) {
        List<String> messages = new ArrayList<>();
        for (AbstractWidget widget : widgets(context)) {
            messages.add(widget.getMessage().getString());
        }
        return messages;
    }

    public static Optional<AbstractWidget> find(ClientGameTestContext context, Predicate<String> label) {
        for (AbstractWidget widget : widgets(context)) {
            if (label.test(widget.getMessage().getString())) {
                return Optional.of(widget);
            }
        }
        return Optional.empty();
    }

    // How many reachable widgets carry a label matching the predicate. Counting rather than indexing is
    // how the settings assertions stay independent of the breadth-first walk order, which is not a
    // contract of anything.
    public static int count(ClientGameTestContext context, Predicate<String> label) {
        int found = 0;
        for (String message : labels(context)) {
            if (label.test(message)) {
                found++;
            }
        }
        return found;
    }

    public static AbstractWidget require(ClientGameTestContext context, String what, Predicate<String> label) {
        return find(context, label).orElseThrow(() -> new AssertionError(
                "No widget matching " + what + " on the open screen; present labels: " + labels(context)));
    }

    // Clicks a widget the way a player would: move the physical cursor onto it, press mouse 1. Going
    // through TestInput rather than calling onPress reflectively is the point - it proves the widget
    // is in the screen's `children` list and wins the hit test, not merely that it was drawn.
    //
    // On 26.3-snapshot-9 the synthetic press produces nothing: the cursor lands (the widget highlights
    // and its tooltip renders, so the move half of TestInput works), but no button activation follows.
    // Rather than lose the whole variant, the click falls back to pressing the widget directly and says
    // so in the log, which keeps every other assertion on that version meaningful. Detection is by
    // fingerprint - the open screen plus every label on it - because a click whose effect is invisible
    // in both is not a click this test is interested in.
    public static void click(ClientGameTestContext context, AbstractWidget widget) {
        List<String> before = fingerprint(context);
        hover(context, widget);
        context.getInput().pressMouse(0);
        context.waitTicks(3);
        if (!fingerprint(context).equals(before)) {
            return;
        }
        Eunomia.LOGGER.warn("[smoke/ui] synthetic mouse press did nothing on '{}'; pressing it directly "
                + "instead (input injection is unsupported on this version)", widget.getMessage().getString());
        context.runOnClient(client -> invokeOnPress(widget));
        context.waitTicks(3);
    }

    // The open screen plus its labels: enough to notice a screen change or a rebuilt row, cheap enough
    // to take around every click.
    private static List<String> fingerprint(ClientGameTestContext context) {
        List<String> state = new ArrayList<>();
        state.add(context.computeOnClient(client -> {
            Screen screen = currentScreen(client);
            return screen == null ? "<none>" : screen.getClass().getName();
        }));
        state.addAll(labels(context));
        return state;
    }

    // Presses a widget without going through input. The signature moved twice inside the supported
    // range - onPress(), then onPress(MouseButtonInfo), then onPress(InputWithModifiers) - so the
    // argument is synthesised from the parameter type rather than named: a proxy for an interface, a
    // zero-filled instance for a record. Both answer "no modifiers held", which is what a plain click is.
    private static void invokeOnPress(AbstractWidget widget) {
        for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!"onPress".equals(method.getName()) || method.getParameterCount() > 1) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(widget, method.getParameterCount() == 0
                            ? new Object[0]
                            : new Object[] { stub(method.getParameterTypes()[0]) });
                    return;
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Could not press " + widget.getMessage().getString(), e);
                }
            }
        }
        throw new AssertionError("No onPress on " + widget.getClass().getName());
    }

    private static Object stub(Class<?> type) throws ReflectiveOperationException {
        if (type.isInterface()) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
        }
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Class<?>[] parameters = constructor.getParameterTypes();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            arguments[i] = defaultValue(parameters[i]);
        }
        return constructor.newInstance(arguments);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        return 0;
    }

    // Parks the cursor over a widget's centre, in physical window pixels. Widget coordinates are in
    // GUI-scaled space, and the scale is derived rather than read off Window#getGuiScale() because
    // that accessor returns a double below 26.2 and an int from 26.2 on.
    public static void hover(ClientGameTestContext context, AbstractWidget widget) {
        int centreX = widget.getX() + widget.getWidth() / 2;
        int centreY = widget.getY() + widget.getHeight() / 2;
        double scale = context.computeOnClient(client ->
                client.getWindow().getWidth() / (double) client.getWindow().getGuiScaledWidth());
        context.getInput().setCursorPos(centreX * scale, centreY * scale);
        context.waitTicks(2);
    }

    // Whether an EditBox currently accepts typing, or empty when this version does not expose the flag
    // under a name we recognise. There is no getter for it on any supported version - only
    // EditBox#setEditable - so the field is read directly, and the caller decides what an unreadable
    // answer is worth rather than being handed a made-up boolean.
    public static Optional<Boolean> editableFlag(AbstractWidget widget) {
        for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != boolean.class || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                // Matches `isEditable` (official/mojmap) and `editable` alike, without pinning either.
                if (!field.getName().toLowerCase(java.util.Locale.ROOT).endsWith("editable")) {
                    continue;
                }
                Object value = read(field, widget);
                if (value instanceof Boolean flag) {
                    return Optional.of(flag);
                }
            }
        }
        return Optional.empty();
    }

    // The ARGB an EditBox draws its own text with, or empty when the field is not where it is expected.
    //
    // Worth reaching for because the alpha byte of that number is a silent correctness trap. Before
    // 1.21.5 the value was RGB and Font substituted full alpha for a zero one; from 1.21.5 it is passed
    // through as ARGB, so the identical constant renders invisible. Nothing throws, nothing logs, the
    // widget reports the right value out of getValue() - the text is simply not there. Only a pixel or
    // a look at this field can tell the difference.
    public static java.util.OptionalInt textColour(AbstractWidget widget) {
        for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != int.class || Modifier.isStatic(field.getModifiers())
                        || !"textColor".equals(field.getName())) {
                    continue;
                }
                Object value = read(field, widget);
                if (value instanceof Integer colour) {
                    return java.util.OptionalInt.of(colour);
                }
            }
        }
        return java.util.OptionalInt.empty();
    }

    // Fails when a widget would draw its text at zero alpha, which is indistinguishable from having no
    // text at all. Silent on a version whose field cannot be found rather than failing, because a
    // renamed private field is not a defect in the thing under test.
    public static void assertTextVisible(ClientGameTestContext context, String what, AbstractWidget widget) {
        java.util.OptionalInt colour = textColour(widget);
        if (colour.isEmpty()) {
            Eunomia.LOGGER.warn("[smoke/ui] no readable text colour on {}; {} is unasserted here",
                    widget.getClass().getName(), what);
            return;
        }
        if ((colour.getAsInt() >>> 24) == 0) {
            throw new AssertionError("DEFECT: " + what + " draws its text at ARGB 0x"
                    + Integer.toHexString(colour.getAsInt()) + " - the alpha byte is zero, so from 1.21.5 "
                    + "on every character the player types into it is fully transparent. getValue() still "
                    + "returns the text, so nothing but a pixel notices.");
        }
    }

    // Fails when any visible label is still a raw translation key - the cheapest possible check that
    // the lang file shipped, was picked up by the resource loader, and spells the keys the code uses.
    public static void assertNoRawKeys(ClientGameTestContext context, String where) {
        List<String> raw = new ArrayList<>();
        for (String label : labels(context)) {
            if (label.startsWith("eunomia.")) {
                raw.add(label);
            }
        }
        if (!raw.isEmpty()) {
            throw new AssertionError("Unresolved eunomia translation key(s) rendered on " + where + ": " + raw);
        }
    }

    private static List<AbstractWidget> collect(Screen screen) {
        List<AbstractWidget> found = new ArrayList<>();
        if (screen == null) {
            return found;
        }
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Object> frontier = new ArrayDeque<>();
        ScreenAccessor accessor = (ScreenAccessor) screen;
        frontier.addAll(accessor.eunomia$getRenderables());
        frontier.addAll(accessor.eunomia$getChildren());
        int budget = NODE_BUDGET;
        while (!frontier.isEmpty() && budget-- > 0) {
            Object node = frontier.poll();
            if (node == null || !seen.add(node)) {
                continue;
            }
            if (node instanceof AbstractWidget widget && !found.contains(widget)) {
                found.add(widget);
            }
            expand(node, frontier);
        }
        return found;
    }

    private static void expand(Object node, Deque<Object> frontier) {
        if (node instanceof Collection<?> collection) {
            frontier.addAll(collection);
            return;
        }
        // A Screen is where the walk stops. Widgets hold back-references to their owning screen
        // (OptionsList keeps one), and a screen in turn reaches its parent screen and the layout object
        // it accumulates across rebuilds - so without this the walk quietly returns the whole screen
        // stack plus every pre-rebuild copy of the rows, and "find the Reset button" starts matching a
        // stale widget nobody can click.
        if (node instanceof Screen) {
            return;
        }
        if (!isWalkable(node.getClass())) {
            return;
        }
        for (Class<?> type = node.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                Object value = read(field, node);
                if (value != null) {
                    frontier.add(value);
                }
            }
        }
    }

    // Only Minecraft's gui types and eunomia's own are walked. Widening this to the whole heap would
    // drag in the client, the level and eventually everything.
    private static boolean isWalkable(Class<?> type) {
        String name = type.getName();
        return name.startsWith("net.minecraft.client.gui.")
                || name.startsWith("net.minecraft.client.Option")
                || name.startsWith("de.zannagh.eunomia.");
    }

    private static Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
//?}
