package de.zannagh.eunomia.common;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link EnrichedLogger} decorates every message with the {@code [Eunomia] - } prefix
 * and forwards each call to the SAME level on the delegate. {@code EnrichedLogger} is ~50 near-
 * identical hand-written {@link Logger} overloads, so the bulk is checked exhaustively by reflection:
 * every declared forwarding method is invoked and verified against the delegate. That coverage is what
 * catches the failure mode this boilerplate is prone to - a copy-pasted body that forwards to the
 * wrong level or drops the prefix.
 */
class EnrichedLoggerTest {

    private static final String PREFIX = "[Eunomia] - ";
    // A message with placeholders so the argument-bearing overloads are exercised realistically.
    private static final String RAW_MESSAGE = "value {} {}";
    private static final Marker MARKER = MarkerFactory.getMarker("eunomia-test");
    private static final Throwable BOOM = new IllegalStateException("boom");
    private static final Object[] VARARGS = {"v0", "v1"};

    private final Logger delegate = mock(Logger.class);
    private final EnrichedLogger logger = new EnrichedLogger(delegate);

    @Test
    void nameIsFixedAndDoesNotConsultTheDelegate() {
        when(delegate.getName()).thenReturn("some-other-name");

        assertThat(logger.getName()).isEqualTo("eunomia-logger");
        // getName() must never fall through to the delegate.
        verifyNoInteractions(delegate);
    }

    @Test
    void nullMessageStillGetsThePrefixConcatenated() {
        // formatMessage concatenates, so a null message becomes the string "[Eunomia] - null".
        logger.info((String) null);

        verify(delegate).info(PREFIX + "null");
    }

    /**
     * Exhaustively drives every {@code void} logging overload (trace/debug/info/warn/error, in their
     * plain, single-arg, two-arg, varargs, throwable and marker-prefixed forms) and asserts the exact
     * same method is called on the delegate with the message prefixed and every other argument intact.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("forwardingMethods")
    void everyForwardingMethodPrefixesTheMessageAndTargetsTheSameLevel(Method method) throws Exception {
        // The methods are enumerated off EnrichedLogger (to filter to its own overrides), but must be
        // invoked through the Logger view: the delegate and verify() proxy are Loggers, not EnrichedLoggers.
        Method target = asLoggerMethod(method);
        Object[] callArgs = sampleArgs(method);

        target.invoke(logger, callArgs);

        target.invoke(verify(delegate), withPrefixedMessage(method, callArgs));
        verifyNoMoreInteractions(delegate);
    }

    /**
     * Exhaustively drives every {@code isXEnabled()} / {@code isXEnabled(Marker)} check and asserts the
     * result is passed straight through from the delegate for that same level.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("enablementMethods")
    void everyEnablementCheckIsDelegatedForTheSameLevel(Method method) throws Exception {
        Method target = asLoggerMethod(method);
        Object[] callArgs = sampleArgs(method);
        when(target.invoke(delegate, callArgs)).thenReturn(true);

        Object result = target.invoke(logger, callArgs);

        assertThat(result).isEqualTo(true);
        target.invoke(verify(delegate), callArgs);
    }

    private static Method asLoggerMethod(Method method) throws NoSuchMethodException {
        return Logger.class.getMethod(method.getName(), method.getParameterTypes());
    }

    private static Stream<Arguments> forwardingMethods() {
        return declaredMethods(m -> m.getReturnType() == void.class);
    }

    private static Stream<Arguments> enablementMethods() {
        return declaredMethods(m -> m.getReturnType() == boolean.class);
    }

    private static Stream<Arguments> declaredMethods(java.util.function.Predicate<Method> keep) {
        return Arrays.stream(EnrichedLogger.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic())
                .filter(keep)
                .sorted(Comparator.comparing(Method::getName)
                        .thenComparing(m -> Arrays.toString(m.getParameterTypes())))
                .map(m -> Arguments.of(Named.of(label(m), m)));
    }

    /** Builds a representative argument for each parameter type EnrichedLogger's overloads use. */
    private static Object[] sampleArgs(Method method) {
        Class<?>[] params = method.getParameterTypes();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i];
            if (p == Marker.class) {
                args[i] = MARKER;
            } else if (p == String.class) {
                args[i] = RAW_MESSAGE;
            } else if (p == Throwable.class) {
                args[i] = BOOM;
            } else if (p == Object[].class) {
                args[i] = VARARGS;
            } else if (p == Object.class) {
                args[i] = "arg" + i;
            } else {
                throw new IllegalStateException("Unhandled parameter type on " + method + ": " + p);
            }
        }
        return args;
    }

    /** Copies the call arguments, replacing the (first, and only) String message with its prefixed form. */
    private static Object[] withPrefixedMessage(Method method, Object[] callArgs) {
        Class<?>[] params = method.getParameterTypes();
        Object[] expected = callArgs.clone();
        for (int i = 0; i < params.length; i++) {
            if (params[i] == String.class) {
                expected[i] = PREFIX + callArgs[i];
                break;
            }
        }
        return expected;
    }

    private static String label(Method method) {
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params[i] == Object[].class ? "Object..." : params[i].getSimpleName());
        }
        return sb.append(')').toString();
    }
}
