package de.zannagh.eunomia.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectiveChainTest {

    /** Terminal fixture whose declared return types drive the reflective chain resolution. */
    public static final class Leaf {
        public String group() {
            return "diamond";
        }

        public int number() {
            return 42;
        }

        public Leaf self() {
            return this;
        }

        public String boom() {
            throw new IllegalStateException("kaboom");
        }
    }

    /** Root fixture exposing no-arg accessors that return {@link Leaf} (or null). */
    public static final class Root {
        private final Leaf leaf;

        public Root(Leaf leaf) {
            this.leaf = leaf;
        }

        public Leaf leaf() {
            return leaf;
        }

        public Leaf nullLeaf() {
            return null;
        }
    }

    @Test
    void resolvesTerminalStringThroughAMultiStepChain() {
        ReflectiveChain chain = new ReflectiveChain("leaf", "group");

        assertThat(chain.resolve(new Root(new Leaf()))).isEqualTo("diamond");
    }

    @Test
    void resolvesSingleStepStringChain() {
        ReflectiveChain chain = new ReflectiveChain("group");

        assertThat(chain.resolve(new Leaf())).isEqualTo("diamond");
    }

    @Test
    void resolveObjectReturnsTheTerminalReceiver() {
        ReflectiveChain chain = new ReflectiveChain("leaf");
        Leaf leaf = new Leaf();

        Object result = chain.resolveObject(new Root(leaf));

        assertThat(result).isSameAs(leaf);
    }

    @Test
    void resolveObjectWalksAllStepsAndReturnsNonStringTerminal() {
        ReflectiveChain chain = new ReflectiveChain("leaf", "self");
        Leaf leaf = new Leaf();

        assertThat(chain.resolveObject(new Root(leaf))).isSameAs(leaf);
    }

    @Test
    void resolveReturnsNullWhenTerminalValueIsNotAString() {
        ReflectiveChain chain = new ReflectiveChain("leaf", "number");
        Root root = new Root(new Leaf());

        // resolve() only yields a value when the terminal is a String; an int (boxed) is not.
        assertThat(chain.resolve(root)).isNull();
        // resolveObject() still hands back the boxed terminal value.
        assertThat(chain.resolveObject(root)).isEqualTo(42);
    }

    @Test
    void nullTargetResolvesToNull() {
        ReflectiveChain chain = new ReflectiveChain("group");

        assertThat(chain.resolve(null)).isNull();
        assertThat(chain.resolveObject(null)).isNull();
    }

    @Test
    void missingMemberMakesTheWholeChainUnresolvableAndReturnsNull() {
        ReflectiveChain chain = new ReflectiveChain("noSuchMethodHere");

        assertThat(chain.resolve(new Leaf())).isNull();
        assertThat(chain.resolveObject(new Leaf())).isNull();
    }

    @Test
    void missingMemberPartwayThroughChainReturnsNull() {
        // "leaf" resolves, but Leaf has no "missing" method, so the whole chain is unresolved.
        ReflectiveChain chain = new ReflectiveChain("leaf", "missing");

        assertThat(chain.resolveObject(new Root(new Leaf()))).isNull();
    }

    @Test
    void nullIntermediateValueShortCircuitsToNull() {
        // nullLeaf() is declared to return Leaf (so the chain resolves), but returns null at runtime.
        ReflectiveChain chain = new ReflectiveChain("nullLeaf", "group");

        assertThat(chain.resolve(new Root(new Leaf()))).isNull();
        assertThat(chain.resolveObject(new Root(new Leaf()))).isNull();
    }

    @Test
    void invocationExceptionIsSwallowedAndYieldsNull() {
        ReflectiveChain chain = new ReflectiveChain("boom");

        assertThat(chain.resolve(new Leaf())).isNull();
        assertThat(chain.resolveObject(new Leaf())).isNull();
    }

    @Test
    void emptyChainAlwaysResolvesToNull() {
        ReflectiveChain chain = new ReflectiveChain();

        // No method names -> the resolved array has length 0 -> treated as unresolvable.
        assertThat(chain.resolveObject(new Leaf())).isNull();
        assertThat(chain.resolve(new Leaf())).isNull();
    }

    @Test
    void resolutionIsStableAcrossRepeatedCallsForTheSameReceiverClass() {
        ReflectiveChain chain = new ReflectiveChain("leaf", "group");
        Root first = new Root(new Leaf());
        Root second = new Root(new Leaf());

        // Second call for the same concrete class hits the cached Method[] chain.
        assertThat(chain.resolve(first)).isEqualTo("diamond");
        assertThat(chain.resolve(second)).isEqualTo("diamond");
    }

    @Test
    void differentReceiverClassesEachResolveIndependently() {
        // "group" resolves on Leaf but not on Root: the ClassValue cache keys on the concrete class.
        ReflectiveChain chain = new ReflectiveChain("group");

        assertThat(chain.resolve(new Leaf())).isEqualTo("diamond");
        assertThat(chain.resolve(new Root(new Leaf()))).isNull();
    }
}
