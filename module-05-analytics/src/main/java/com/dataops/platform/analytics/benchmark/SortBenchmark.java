package com.dataops.platform.analytics.benchmark;

import com.dataops.platform.core.algorithm.Sorter;
import org.openjdk.jmh.annotations.*;

import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class SortBenchmark {

    @Param({"10000", "100000", "1000000"})
    private int size;

    private Integer[] data;

    @Setup
    public void setup() {
        data = new Integer[size];
        Random r = new Random(42);
        for (int i = 0; i < size; i++) {
            data[i] = r.nextInt(1000000);
        }
    }

    @Benchmark
    public void quickSort() {
        Integer[] copy = data.clone();
        Sorter.quickSort(copy, Comparator.naturalOrder());
    }

    @Benchmark
    public void mergeSort() {
        Integer[] copy = data.clone();
        Sorter.mergeSort(copy, Comparator.naturalOrder());
    }

    @Benchmark
    public void heapSort() {
        Integer[] copy = data.clone();
        Sorter.heapSort(copy, Comparator.naturalOrder());
    }
}