package de.zannagh.eunomia.networking.payloads;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EunomiaPayloadListTest {

    @Test
    void copiesSourceElementsInOrder() {
        var list = new EunomiaPayloadList<>(List.of("a", "b", "c"));

        assertThat(list).containsExactly("a", "b", "c");
        assertThat(list).hasSize(3);
    }

    @Test
    void constructionFromEmptyCollectionYieldsEmptyList() {
        var list = new EunomiaPayloadList<>(List.of());

        assertThat(list).isEmpty();
    }

    @Test
    void isDefensiveCopyOfMutableSource() {
        var source = new ArrayList<>(List.of("x", "y"));
        var list = new EunomiaPayloadList<>(source);

        source.add("z");

        // Later mutation of the source must not leak into the copy.
        assertThat(list).containsExactly("x", "y");
    }

    @Test
    void mutatingListDoesNotAffectSource() {
        var source = new ArrayList<>(List.of("x", "y"));
        var list = new EunomiaPayloadList<>(source);

        list.add("z");

        assertThat(source).containsExactly("x", "y");
        assertThat(list).containsExactly("x", "y", "z");
    }

    @Test
    void nullSourceCollectionThrows() {
        assertThatThrownBy(() -> new EunomiaPayloadList<>(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isAnArrayListForInheritedBehaviour() {
        var list = new EunomiaPayloadList<>(List.of(1, 2));

        assertThat(list).isInstanceOf(ArrayList.class);
        assertThat(list).isInstanceOf(List.class);
    }

    @Test
    void markerTypeDistinguishesFromPlainArrayList() {
        // The whole point of the class is the instanceof marker used to break recursion.
        List<String> plain = new ArrayList<>(List.of("a"));
        List<String> marked = new EunomiaPayloadList<>(List.of("a"));

        assertThat(plain).isNotInstanceOf(EunomiaPayloadList.class);
        assertThat(marked).isInstanceOf(EunomiaPayloadList.class);
    }

    @Test
    void supportsIndexedAccessAndIteration() {
        var list = new EunomiaPayloadList<>(List.of("first", "second"));

        assertThat(list.get(0)).isEqualTo("first");
        assertThat(list.get(1)).isEqualTo("second");

        Iterator<String> it = list.iterator();
        assertThat(it.next()).isEqualTo("first");
        assertThat(it.next()).isEqualTo("second");
        assertThat(it.hasNext()).isFalse();
    }

    @Test
    void equalsIsContentBasedAcrossListTypes() {
        var marked = new EunomiaPayloadList<>(List.of("a", "b"));
        var plain = new ArrayList<>(List.of("a", "b"));

        // List.equals is content-based, so a marked list equals a plain list with equal contents.
        assertThat(marked).isEqualTo(plain);
        assertThat(plain).isEqualTo(marked);
        assertThat(marked.hashCode()).isEqualTo(plain.hashCode());
    }

    @Test
    void notEqualWhenContentsDiffer() {
        var a = new EunomiaPayloadList<>(List.of("a", "b"));
        var b = new EunomiaPayloadList<>(List.of("a", "c"));

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void serializesAndDeserializesPreservingTypeAndContents() throws Exception {
        var original = new EunomiaPayloadList<>(List.of("one", "two", "three"));

        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }

        Object restored;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = in.readObject();
        }

        assertThat(restored).isInstanceOf(EunomiaPayloadList.class);
        assertThat(restored).isEqualTo(original);
        @SuppressWarnings("unchecked")
        var restoredList = (EunomiaPayloadList<String>) restored;
        assertThat(restoredList).containsExactly("one", "two", "three");
    }

    @Test
    void supportsNullElements() {
        var withNull = new ArrayList<String>();
        withNull.add("a");
        withNull.add(null);
        var list = new EunomiaPayloadList<>(withNull);

        assertThat(list).containsExactly("a", null);
        assertThat(list.contains(null)).isTrue();
    }
}
