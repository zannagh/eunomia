package de.zannagh.eunomia.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrioritizedHandlerTest {

    /** Minimal concrete handler that echoes the event and exposes a configurable priority. */
    private static final class FixedHandler implements PrioritizedHandler<String> {
        private final int priority;
        private final boolean shouldHandle;
        private final boolean exclusive;

        FixedHandler(int priority, boolean shouldHandle, boolean exclusive) {
            this.priority = priority;
            this.shouldHandle = shouldHandle;
            this.exclusive = exclusive;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public boolean shouldHandle(String event) {
            return shouldHandle;
        }

        @Override
        public boolean shouldHandleExclusively(String event) {
            return exclusive;
        }

        @Override
        public String handle(String event) {
            return event;
        }
    }

    /** Handler that does not override getPriority, so the interface default applies. */
    private static final class DefaultPriorityHandler implements PrioritizedHandler<String> {
        @Override
        public boolean shouldHandle(String event) {
            return true;
        }

        @Override
        public boolean shouldHandleExclusively(String event) {
            return false;
        }

        @Override
        public String handle(String event) {
            return event;
        }
    }

    @Test
    void defaultPriorityConstantIsHundred() {
        assertThat(PrioritizedHandler.DEFAULT_PRIORITY).isEqualTo(100);
    }

    @Test
    void defaultGetPriorityReturnsDefaultConstant() {
        var handler = new DefaultPriorityHandler();
        assertThat(handler.getPriority()).isEqualTo(PrioritizedHandler.DEFAULT_PRIORITY);
    }

    @Test
    void comparatorSortsAscendingByPriority() {
        var high = new FixedHandler(300, true, false);
        var low = new FixedHandler(10, true, false);
        var mid = new FixedHandler(100, true, false);

        var handlers = new ArrayList<PrioritizedHandler<String>>(List.of(high, mid, low));
        handlers.sort(Comparator.comparingInt(PrioritizedHandler::getPriority));

        assertThat(handlers).containsExactly(low, mid, high);
    }

    @Test
    void comparatorHandlesNegativePriorities() {
        var negative = new FixedHandler(-50, true, false);
        var zero = new FixedHandler(0, true, false);
        var positive = new FixedHandler(50, true, false);

        var handlers = new ArrayList<PrioritizedHandler<String>>(List.of(positive, zero, negative));
        handlers.sort(Comparator.comparingInt(PrioritizedHandler::getPriority));

        assertThat(handlers).containsExactly(negative, zero, positive);
        assertThat(handlers.get(0).getPriority()).isEqualTo(-50);
    }

    @Test
    void comparatorIsStableForTiedPriorities() {
        var first = new FixedHandler(100, true, false);
        var second = new FixedHandler(100, true, false);
        var third = new FixedHandler(100, true, false);

        var handlers = new ArrayList<PrioritizedHandler<String>>(List.of(first, second, third));
        handlers.sort(Comparator.comparingInt(PrioritizedHandler::getPriority));

        // List.sort is stable: equal priorities retain insertion order.
        assertThat(handlers).containsExactly(first, second, third);
    }

    @Test
    void comparatorWorksWithMockedPriorities() {
        @SuppressWarnings("unchecked")
        PrioritizedHandler<String> a = mock(PrioritizedHandler.class);
        @SuppressWarnings("unchecked")
        PrioritizedHandler<String> b = mock(PrioritizedHandler.class);
        when(a.getPriority()).thenReturn(5);
        when(b.getPriority()).thenReturn(1);

        var handlers = new ArrayList<>(List.of(a, b));
        handlers.sort(Comparator.comparingInt(PrioritizedHandler::getPriority));

        assertThat(handlers).containsExactly(b, a);
    }

    @Test
    void shouldHandleAndExclusivelyReflectHandlerState() {
        var exclusive = new FixedHandler(1, true, true);
        var passive = new FixedHandler(1, false, false);

        assertThat(exclusive.shouldHandle("evt")).isTrue();
        assertThat(exclusive.shouldHandleExclusively("evt")).isTrue();
        assertThat(passive.shouldHandle("evt")).isFalse();
        assertThat(passive.shouldHandleExclusively("evt")).isFalse();
    }
}
