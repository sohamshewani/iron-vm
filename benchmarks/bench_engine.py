package com.ironvm.benchmark;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class IronVmMicrobenchmark {

    // Microbenchmark configuration parameters
    private static final int WARMUP_ITERATIONS = 50_000;
    private static final int MEASUREMENT_ITERATIONS = 500_000;
    private static final int PERCENTILE_SAMPLES = MEASUREMENT_ITERATIONS;

    public static void main(String[] args) {
        // Allocate array to store latency samples in nanoseconds for high-resolution measurement
        long[] latencies = new long[PERCENTILE_SAMPLES];

        // Simulate a lightweight VM execution context setup
        // Replace this with actual 'iron-vm' instantiation and bytecode loading when integrated
        VmContext context = new VmContext();

        // -----------------------------------------------------------------
        // 1. WARMUP PHASE (JIT compilation, cache warming, class loading)
        // -----------------------------------------------------------------
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executeOperation(context);
        }

        // Force GC to stabilize memory before measurement
        System.gc();

        // -----------------------------------------------------------------
        // 2. MEASUREMENT PHASE
        // -----------------------------------------------------------------
        long startTime = System.nanoTime();

        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long opStart = System.nanoTime();
            executeOperation(context);
            long opEnd = System.nanoTime();
            
            latencies[i] = opEnd - opStart;
        }

        long endTime = System.nanoTime();
        long totalDurationNs = endTime - startTime;

        // -----------------------------------------------------------------
        // 3. METRICS PROCESSING
        // -----------------------------------------------------------------
        Arrays.sort(latencies);

        double totalDurationSec = totalDurationNs / 1_000_000_000.0;
        double opsPerSec = MEASUREMENT_ITERATIONS / totalDurationSec;

        long p50Ns = latencies[indexAtPercentile(50.0, PERCENTILE_SAMPLES)];
        long p99Ns = latencies[indexAtPercentile(99.0, PERCENTILE_SAMPLES)];
        long p999Ns = latencies[indexAtPercentile(99.9, PERCENTILE_SAMPLES)];

        // -----------------------------------------------------------------
        // 4. FORMATTED TABULAR OUTPUT TO STDOUT
        // -----------------------------------------------------------------
        printTabularResults(opsPerSec, totalDurationSec, p50Ns, p99Ns, p999Ns);
    }

    private static void executeOperation(VmContext context) {
        // Target benchmark workload representing a basic 'iron-vm' operation cycle:
        // Fetch -> Decode -> Execute (Simulated via lightweight arithmetic/state mutation)
        context.instructionPointer = (context.instructionPointer + 1) & 0xFF;
        context.accumulator += context.instructionPointer;
    }

    private static int indexAtPercentile(double percentile, int size) {
        int index = (int) Math.ceil((percentile / 100.0) * size) - 1;
        return Math.max(0, Math.min(index, size - 1));
    }

    private static void printTabularResults(double opsPerSec, double durationSec, long p50Ns, long p99Ns, long p999Ns) {
        String line = "+------------------------+--------------------------+";
        System.out.println(line);
        System.out.println("| Metric                 | Value                    |");
        System.out.println(line);
        System.out.printf("| %-22s | %22.2f |\n", "Throughput (ops/sec)", opsPerSec);
        System.out.printf("| %-22s | %22.4f |\n", "Total Duration (s)", durationSec);
        System.out.printf("| %-22s | %19.2f ms |\n", "Latency p50", p50Ns / 1_000_000.0);
        System.out.printf("| %-22s | %19.2f ms |\n", "Latency p99", p99Ns / 1_000_000.0);
        System.out.printf("| %-22s | %19.2f ms |\n", "Latency p99.9", p999Ns / 1_000_000.0);
        System.out.println(line);
    }

    private static class VmContext {
        int instructionPointer = 0;
        long accumulator = 0;
    }
}