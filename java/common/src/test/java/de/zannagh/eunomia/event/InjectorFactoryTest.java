package de.zannagh.eunomia.event;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InjectorFactoryTest {

    /** Concrete factory over a plain String event so the abstract type can be instantiated. */
    private static final class StringInjectorFactory extends InjectorFactory<String, PrioritizedHandler<String>> {
        StringInjectorFactory(PrioritizedHandler<String> defaultHandler) {
            super(defaultHandler);
        }
    }

    /** Configurable real handler; appends its tag to the event when handling. */
    private static final class TaggingHandler implements PrioritizedHandler<String> {
        private final int priority;
        private final boolean handles;
        private final boolean exclusive;
        private final String tag;

        TaggingHandler(int priority, boolean handles, boolean exclusive, String tag) {
            this.priority = priority;
            this.handles = handles;
            this.exclusive = exclusive;
            this.tag = tag;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public boolean shouldHandle(String event) {
            return handles;
        }

        @Override
        public boolean shouldHandleExclusively(String event) {
            return exclusive;
        }

        @Override
        public String handle(String event) {
            return event + tag;
        }
    }

    private static PrioritizedHandler<String> passiveDefault() {
        return new TaggingHandler(PrioritizedHandler.DEFAULT_PRIORITY, false, false, "-def");
    }

    @Test
    void constructorRegistersDefaultHandler() {
        var def = passiveDefault();
        var factory = new StringInjectorFactory(def);

        assertThat(factory.getInstances()).containsExactly(def);
        assertThat(factory.getPreferredHandler()).isSameAs(def);
    }

    @Test
    void addHandlerKeepsInstancesSortedAscendingByPriority() {
        var factory = new StringInjectorFactory(new TaggingHandler(100, false, false, "-def"));
        var high = new TaggingHandler(500, false, false, "-high");
        var low = new TaggingHandler(1, false, false, "-low");

        factory.addHandler(high);
        factory.addHandler(low);

        assertThat(factory.getInstances())
                .extracting(PrioritizedHandler::getPriority)
                .containsExactly(1, 100, 500);
        assertThat(factory.getPreferredHandler().getPriority()).isEqualTo(1);
    }

    @Test
    void addHandlerSortsNegativePrioritiesFirst() {
        var factory = new StringInjectorFactory(new TaggingHandler(0, false, false, "-zero"));
        var negative = new TaggingHandler(-25, false, false, "-neg");
        var positive = new TaggingHandler(25, false, false, "-pos");

        factory.addHandler(positive);
        factory.addHandler(negative);

        assertThat(factory.getInstances())
                .extracting(PrioritizedHandler::getPriority)
                .containsExactly(-25, 0, 25);
    }

    @Test
    void getInstancesForFiltersOutHandlersThatDoNotHandle() {
        var factory = new StringInjectorFactory(new TaggingHandler(1, false, false, "-def"));
        var accepting = new TaggingHandler(2, true, false, "-yes");
        var rejecting = new TaggingHandler(3, false, false, "-no");
        factory.addHandler(accepting);
        factory.addHandler(rejecting);

        var selected = factory.getInstancesFor("event");

        assertThat(selected).containsExactly(accepting);
    }

    @Test
    void getInstancesForReturnsEmptyWhenNothingHandles() {
        var factory = new StringInjectorFactory(new TaggingHandler(1, false, false, "-def"));

        assertThat(factory.getInstancesFor("event")).isEmpty();
    }

    @Test
    void getInstancesForPreservesPriorityOrderingOfHandlers() {
        var factory = new StringInjectorFactory(new TaggingHandler(300, true, false, "-c"));
        var first = new TaggingHandler(10, true, false, "-a");
        var second = new TaggingHandler(20, true, false, "-b");
        factory.addHandler(second);
        factory.addHandler(first);

        var selected = factory.getInstancesFor("event");

        assertThat(selected)
                .extracting(PrioritizedHandler::getPriority)
                .containsExactly(10, 20, 300);
    }

    @Test
    void getInstancesForReturnsOnlyTheFirstExclusiveHandlerBySortedOrder() {
        var factory = new StringInjectorFactory(new TaggingHandler(100, true, false, "-def"));
        var lowExclusive = new TaggingHandler(5, true, true, "-lowEx");
        var highExclusive = new TaggingHandler(50, true, true, "-highEx");
        var normal = new TaggingHandler(1, true, false, "-normal");
        factory.addHandler(highExclusive);
        factory.addHandler(lowExclusive);
        factory.addHandler(normal);

        var selected = factory.getInstancesFor("event");

        // Exclusive present -> single handler, the lowest-priority (first sorted) exclusive one.
        assertThat(selected).containsExactly(lowExclusive);
    }

    @Test
    void getInstancesForIgnoresExclusiveHandlersThatDoNotHandle() {
        var def = new TaggingHandler(100, true, false, "-def");
        var factory = new StringInjectorFactory(def);
        // Exclusive but does not handle the event -> must not short-circuit.
        var exclusiveButRejects = new TaggingHandler(1, false, true, "-ex");
        var accepting = new TaggingHandler(2, true, false, "-yes");
        factory.addHandler(exclusiveButRejects);
        factory.addHandler(accepting);

        var selected = factory.getInstancesFor("event");

        assertThat(selected).containsExactly(accepting, def);
        assertThat(selected).doesNotContain(exclusiveButRejects);
    }

    @Test
    void findHandlerReturnsFirstMatchInPriorityOrder() {
        var factory = new StringInjectorFactory(new TaggingHandler(100, false, false, "-def"));
        var lowTagged = new TaggingHandler(1, true, false, "-target");
        var highTagged = new TaggingHandler(200, true, false, "-target");
        factory.addHandler(highTagged);
        factory.addHandler(lowTagged);

        var found = factory.findHandler(h -> h.shouldHandle("x"));

        assertThat(found).containsSame(lowTagged);
    }

    @Test
    void findHandlerReturnsEmptyWhenNoMatch() {
        var factory = new StringInjectorFactory(new TaggingHandler(100, false, false, "-def"));

        assertThat(factory.findHandler(h -> h.getPriority() < 0)).isEmpty();
    }

    @Test
    void anyHandlerReflectsPredicateMatch() {
        var factory = new StringInjectorFactory(new TaggingHandler(100, false, false, "-def"));
        factory.addHandler(new TaggingHandler(5, true, false, "-x"));

        assertThat(factory.anyHandler(h -> h.getPriority() == 5)).isTrue();
        assertThat(factory.anyHandler(h -> h.getPriority() == 999)).isFalse();
    }

    @Test
    void handleDispatchesToEachSelectedHandlerInPriorityOrder() {
        @SuppressWarnings("unchecked")
        PrioritizedHandler<String> low = mock(PrioritizedHandler.class);
        @SuppressWarnings("unchecked")
        PrioritizedHandler<String> high = mock(PrioritizedHandler.class);

        // Selection (getInstancesFor) is evaluated against the ORIGINAL event "start" for every handler.
        when(low.getPriority()).thenReturn(1);
        when(low.shouldHandle("start")).thenReturn(true);
        when(low.shouldHandleExclusively("start")).thenReturn(false);
        when(low.handle("start")).thenReturn("afterLow");

        when(high.getPriority()).thenReturn(2);
        when(high.shouldHandle("start")).thenReturn(true);
        when(high.shouldHandleExclusively("start")).thenReturn(false);
        when(high.handle("afterLow")).thenReturn("afterHigh");

        var factory = new StringInjectorFactory(low);
        factory.addHandler(high);

        factory.handle("start");

        // getInstancesFor is evaluated against the ORIGINAL event once; then handlers run in order,
        // each receiving the previous handler's mutated output.
        var order = inOrder(low, high);
        order.verify(low).handle("start");
        order.verify(high).handle("afterLow");
    }

    @Test
    void handleSkipsHandlersThatDoNotHandleTheEvent() {
        @SuppressWarnings("unchecked")
        PrioritizedHandler<String> active = mock(PrioritizedHandler.class);
        @SuppressWarnings("unchecked")
        PrioritizedHandler<String> inactive = mock(PrioritizedHandler.class);

        when(active.getPriority()).thenReturn(1);
        when(active.shouldHandle("evt")).thenReturn(true);
        when(active.shouldHandleExclusively("evt")).thenReturn(false);
        when(active.handle("evt")).thenReturn("evt");

        when(inactive.getPriority()).thenReturn(2);
        when(inactive.shouldHandle("evt")).thenReturn(false);

        var factory = new StringInjectorFactory(active);
        factory.addHandler(inactive);

        factory.handle("evt");

        verify(active, times(1)).handle("evt");
        verify(inactive, never()).handle("evt");
    }

    @Test
    void handleRoutesExclusivelyToSingleHandler() {
        @SuppressWarnings("unchecked")
        PrioritizedHandler<String> exclusive = mock(PrioritizedHandler.class);
        @SuppressWarnings("unchecked")
        PrioritizedHandler<String> other = mock(PrioritizedHandler.class);

        when(exclusive.getPriority()).thenReturn(1);
        when(exclusive.shouldHandle("evt")).thenReturn(true);
        when(exclusive.shouldHandleExclusively("evt")).thenReturn(true);
        when(exclusive.handle("evt")).thenReturn("evt");

        when(other.getPriority()).thenReturn(2);
        when(other.shouldHandle("evt")).thenReturn(true);
        when(other.shouldHandleExclusively("evt")).thenReturn(false);

        var factory = new StringInjectorFactory(exclusive);
        factory.addHandler(other);

        factory.handle("evt");

        verify(exclusive).handle("evt");
        verify(other, never()).handle("evt");
    }

    @Test
    void getInstancesExposesLiveBackingList() {
        var def = passiveDefault();
        var factory = new StringInjectorFactory(def);
        var added = new TaggingHandler(1, false, false, "-x");
        factory.addHandler(added);

        // getInstances returns the internal, sorted ArrayList reference.
        assertThat(factory.getInstances()).containsExactly(added, def);
    }

    @Test
    void handleWithNoSelectedHandlersDoesNothing() {
        var factory = new StringInjectorFactory(new TaggingHandler(1, false, false, "-def"));

        // Should not throw even though getInstancesFor is empty.
        factory.handle("evt");

        assertThat(factory.getInstancesFor("evt")).isEmpty();
    }

    @Test
    void multipleFactoriesDoNotShareState() {
        var a = new StringInjectorFactory(new TaggingHandler(1, false, false, "-a"));
        var b = new StringInjectorFactory(new TaggingHandler(1, false, false, "-b"));
        a.addHandler(new TaggingHandler(2, false, false, "-a2"));

        assertThat(a.getInstances()).hasSize(2);
        assertThat(b.getInstances()).hasSize(1);
    }

    @Test
    void selectedHandlersMatchExpectedListForMixedSet() {
        var def = new TaggingHandler(100, true, false, "-def");
        var factory = new StringInjectorFactory(def);
        var accept1 = new TaggingHandler(10, true, false, "-1");
        var accept2 = new TaggingHandler(20, true, false, "-2");
        var reject = new TaggingHandler(15, false, false, "-r");
        factory.addHandler(accept2);
        factory.addHandler(accept1);
        factory.addHandler(reject);

        var selected = factory.getInstancesFor("evt");

        // Accepting handlers only, in ascending priority order; the rejecting one is excluded.
        assertThat(selected).containsExactly(accept1, accept2, def);
    }
}
