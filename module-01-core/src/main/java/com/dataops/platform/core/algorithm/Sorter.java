// src/main/java/com/dataops/platform/core/algorithm/Sorter.java
package com.dataops.platform.core.algorithm;

import java.util.Comparator;

/**
 * Manual implementations of classic sorting algorithms.
 * All methods sort the array **in-place**.
 * Used for educational and demonstration purposes.
 *
 * @since 1.0
 */
public final class Sorter {

    private Sorter() {
        // utility class
    }

    /* ====================== QUICK SORT ====================== */

    /**
     * Sorts array using QuickSort (average O(n log n), worst O(n²)).
     */
    public static <T> void quickSort(T[] array, Comparator<? super T> comparator) {
        if (array == null || array.length < 2) return;
        quickSort(array, 0, array.length - 1, comparator);
    }

    private static <T> void quickSort(T[] a, int low, int high, Comparator<? super T> c) {
        if (low < high) {
            int pi = partition(a, low, high, c);
            quickSort(a, low, pi - 1, c);
            quickSort(a, pi + 1, high, c);
        }
    }

    private static <T> int partition(T[] a, int low, int high, Comparator<? super T> c) {
        T pivot = a[high];
        int i = low - 1;
        for (int j = low; j < high; j++) {
            if (c.compare(a[j], pivot) <= 0) {
                i++;
                swap(a, i, j);
            }
        }
        swap(a, i + 1, high);
        return i + 1;
    }

    /* ====================== MERGE SORT ====================== */

    /**
     * Sorts array using MergeSort (stable, always O(n log n)).
     */
    public static <T> void mergeSort(T[] array, Comparator<? super T> comparator) {
        if (array == null || array.length < 2) return;
        @SuppressWarnings("unchecked")
        T[] aux = (T[]) new Object[array.length];
        mergeSort(array, aux, 0, array.length - 1, comparator);
    }

    private static <T> void mergeSort(T[] a, T[] aux, int low, int high, Comparator<? super T> c) {
        if (low >= high) return;
        int mid = low + (high - low) / 2;
        mergeSort(a, aux, low, mid, c);
        mergeSort(a, aux, mid + 1, high, c);
        if (c.compare(a[mid], a[mid + 1]) <= 0) return; // уже отсортировано
        merge(a, aux, low, mid, high, c);
    }

    private static <T> void merge(T[] a, T[] aux, int low, int mid, int high, Comparator<? super T> c) {
        System.arraycopy(a, low, aux, low, high - low + 1);

        int i = low;
        int j = mid + 1;
        int k = low;

        while (i <= mid && j <= high) {
            if (c.compare(aux[i], aux[j]) <= 0) {
                a[k++] = aux[i++];
            } else {
                a[k++] = aux[j++];
            }
        }
        while (i <= mid) {
            a[k++] = aux[i++];
        }
    }

    /* ====================== HEAP SORT ====================== */

    /**
     * Sorts array using HeapSort (in-place, O(n log n)).
     */
    public static <T> void heapSort(T[] array, Comparator<? super T> comparator) {
        if (array == null || array.length < 2) return;

        int n = array.length;

        // Build max-heap
        for (int i = n / 2 - 1; i >= 0; i--) {
            heapify(array, n, i, comparator);
        }

        // Extract elements from heap one by one
        for (int i = n - 1; i > 0; i--) {
            swap(array, 0, i);
            heapify(array, i, 0, comparator);
        }
    }

    private static <T> void heapify(T[] a, int n, int i, Comparator<? super T> c) {
        int largest = i;
        int left = 2 * i + 1;
        int right = 2 * i + 2;

        if (left < n && c.compare(a[left], a[largest]) > 0) {
            largest = left;
        }
        if (right < n && c.compare(a[right], a[largest]) > 0) {
            largest = right;
        }

        if (largest != i) {
            swap(a, i, largest);
            heapify(a, n, largest, c);
        }
    }

    /* ====================== UTILS ====================== */

    private static <T> void swap(T[] a, int i, int j) {
        T temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }

    public static <T extends Comparable<T>> void quickSort(T[] array) {
        quickSort(array, Comparator.naturalOrder());
    }

    public static <T extends Comparable<T>> void mergeSort(T[] array) {
        mergeSort(array, Comparator.naturalOrder());
    }

    public static <T extends Comparable<T>> void heapSort(T[] array) {
        heapSort(array, Comparator.naturalOrder());
    }
}