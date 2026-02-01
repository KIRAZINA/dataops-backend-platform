// module-01-core/src/test/java/com/dataops/platform/core/collection/DynamicArrayTest.java
package com.dataops.platform.core.collection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicArrayTest {

    @Test
    void shouldGrowAndRetrieveElements() {
        DynamicArray<String> arr = new DynamicArray<>(2);
        arr.add("A");
        arr.add("B");
        arr.add("C"); // was "C("C"
        assertEquals(3, arr.size());
        assertEquals("A", arr.get(0));
        assertEquals("B", arr.get(1));
        assertEquals("C", arr.get(2));
    }

    @Test
    void shouldThrowIndexOutOfBounds() {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.add(100);
        assertThrows(IndexOutOfBoundsException.class, () -> arr.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> arr.get(-1));
    }
}