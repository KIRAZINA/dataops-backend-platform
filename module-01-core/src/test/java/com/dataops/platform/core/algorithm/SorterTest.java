// src/test/java/com/dataops/platform/core/algorithm/SorterTest.java
package com.dataops.platform.core.algorithm;

import org.junit.jupiter.api.Test;
import java.util.Comparator;
import static org.junit.jupiter.api.Assertions.*;

class SorterTest {

    private final Comparator<Integer> INT_CMP = Integer::compareTo;

    @Test
    void quickSort_shouldSortCorrectly() {
        Integer[] arr = {5, 3, 8, 1, 2};
        Sorter.quickSort(arr, INT_CMP);
        assertArrayEquals(new Integer[]{1, 2, 3, 5, 8}, arr);
    }

    @Test
    void mergeSort_shouldSortCorrectly() {
        Integer[] arr = {5, 3, 8, 1, 2};
        Sorter.mergeSort(arr, INT_CMP);
        assertArrayEquals(new Integer[]{1, 2, 3, 5, 8}, arr);
    }

    @Test
    void heapSort_shouldSortCorrectly() {
        Integer[] arr = {5, 3, 8, 1, 2};
        Sorter.heapSort(arr, INT_CMP);
        assertArrayEquals(new Integer[]{1, 2, 3, 5, 8}, arr);
    }

    @Test
    void allAlgorithms_shouldProduceSameResult() {
        Integer[] data = {64, 34, 25, 12, 22, 11, 90};

        Integer[] q = data.clone();
        Integer[] m = data.clone();
        Integer[] h = data.clone();

        Sorter.quickSort(q, INT_CMP);
        Sorter.mergeSort(m, INT_CMP);
        Sorter.heapSort(h, INT_CMP);

        assertArrayEquals(m, q);
        assertArrayEquals(m, h);
    }
}