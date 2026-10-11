package com.iron.vm.memory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

public final class GarbageCollector {
    private static final Logger LOGGER = Logger.getLogger(GarbageCollector.class.getName());

    private final MemorySegment heapSegment;
    private final long heapCapacityBytes;
    private final AtomicInteger safepointState;

    private static final long HEADER_SIZE = 16;
    private static final byte MAGIC_FREE = (byte) 0xAA;
    private static final byte MAGIC_ALLOCATED = (byte) 0x55;

    private long freePointer;
    private long liveBytes;
    private long totalAllocatedBytes;
    private boolean isCollecting;

    public GarbageCollector(MemorySegment heapSegment, AtomicInteger safepointState) {
        if (heapSegment == null) {
            throw new IllegalArgumentException("Heap memory segment cannot be null.");
        }
        if (safepointState == null) {
            throw new IllegalArgumentException("Safepoint state atomic reference cannot be null.");
        }
        this.heapSegment = heapSegment;
        this.heapCapacityBytes = heapSegment.byteSize();
        this.safepointState = safepointState;
        this.freePointer = 0L;
        this.liveBytes = 0L;
        this.totalAllocatedBytes = 0L;
        this.isCollecting = false;

        initializeHeap();
    }

    private void initializeHeap() {
        if (heapCapacityBytes < HEADER_SIZE) {
            throw new IllegalStateException("Heap capacity is too small to fit any allocation headers.");
        }
        heapSegment.set(ValueLayout.JAVA_BYTE, 0L, MAGIC_FREE);
        heapSegment.set(ValueLayout.JAVA_LONG, 1L, heapCapacityBytes - HEADER_SIZE);
        heapSegment.set(ValueLayout.JAVA_LONG, 9L, 0L);
    }

    public synchronized long allocate(long payloadSize, long rootContextAddress) {
        long alignedSize = (payloadSize + 7L) & ~7L;
        long requiredBlockSize = HEADER_SIZE + alignedSize;

        if (freePointer + requiredBlockSize > heapCapacityBytes) {
            triggerSynchronousCollection(rootContextAddress);
            if (freePointer + requiredBlockSize > heapCapacityBytes) {
                throw new OutOfMemoryError("Virtual Machine Out-Of-Memory: Heap exhausted after full GC cycle.");
            }
        }

        long allocatedAddress = freePointer;
        heapSegment.set(ValueLayout.JAVA_BYTE, allocatedAddress, MAGIC_ALLOCATED);
        heapSegment.set(ValueLayout.JAVA_LONG, allocatedAddress + 1L, alignedSize);
        heapSegment.set(ValueLayout.JAVA_LONG, allocatedAddress + 9L, 0L);

        freePointer += requiredBlockSize;
        totalAllocatedBytes += alignedSize;
        liveBytes += alignedSize;

        return allocatedAddress + HEADER_SIZE;
    }

    public void triggerSynchronousCollection(long rootContextAddress) {
        synchronized (this) {
            if (isCollecting) {
                return;
            }
            isCollecting = true;
        }

        try {
            safepointState.set(1);
            
            markPhase(rootContextAddress);
            sweepAndCompactPhase();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error during Garbage Collection execution", e);
            throw new VirtualMachineError("GC Failure: " + e.getMessage());
        } finally {
            safepointState.set(0);
            synchronized (this) {
                isCollecting = false;
            }
        }
    }

    private void markPhase(long rootContextAddress) {
        long current = 0L;
        while (current < freePointer) {
            byte magic = heapSegment.get(ValueLayout.JAVA_BYTE, current);
            if (magic != MAGIC_ALLOCATED && magic != MAGIC_FREE) {
                throw new IllegalStateException("Heap corruption detected at address: " + current);
            }
            long blockSize = heapSegment.get(ValueLayout.JAVA_LONG, current + 1L);
            
            if (magic == MAGIC_ALLOCATED) {
                long payloadAddress = current + HEADER_SIZE;
                boolean isReachable = traceReference(payloadAddress, rootContextAddress);
                if (isReachable) {
                    heapSegment.set(ValueLayout.JAVA_LONG, current + 9L, 1L);
                } else {
                    heapSegment.set(ValueLayout.JAVA_LONG, current + 9L, 0L);
                }
            }
            current += HEADER_SIZE + blockSize;
        }
    }

    private boolean traceReference(long payloadAddress, long rootContextAddress) {
        if (payloadAddress == rootContextAddress) {
            return true;
        }
        long headerAddress = payloadAddress - HEADER_SIZE;
        long markBit = heapSegment.get(ValueLayout.JAVA_LONG, headerAddress + 9L);
        return markBit == 1L;
    }

    private void sweepAndCompactPhase() {
        long readPointer = 0L;
        long writePointer = 0L;
        long newlyLiveBytes = 0L;

        while (readPointer < freePointer) {
            byte magic = heapSegment.get(ValueLayout.JAVA_BYTE, readPointer);
            long blockSize = heapSegment.get(ValueLayout.JAVA_LONG, readPointer + 1L);
            long markBit = heapSegment.get(ValueLayout.JAVA_LONG, readPointer + 9L);

            long totalBlockSize = HEADER_SIZE + blockSize;

            if (magic == MAGIC_ALLOCATED && markBit == 1L) {
                if (readPointer != writePointer) {
                    MemorySegment.copy(heapSegment, readPointer, heapSegment, writePointer, totalBlockSize);
                }
                heapSegment.set(ValueLayout.JAVA_LONG, writePointer + 9L, 0L);
                newlyLiveBytes += blockSize;
                writePointer += totalBlockSize;
            }

            readPointer += totalBlockSize;
        }

        freePointer = writePointer;
        liveBytes = newlyLiveBytes;

        long remainingSpace = heapCapacityBytes - freePointer;
        if (remainingSpace >= HEADER_SIZE) {
            heapSegment.set(ValueLayout.JAVA_BYTE, freePointer, MAGIC_FREE);
            heapSegment.set(ValueLayout.JAVA_LONG, freePointer + 1L, remainingSpace - HEADER_SIZE);
            heapSegment.set(ValueLayout.JAVA_LONG, freePointer + 9L, 0L);
        }
    }

    public synchronized long getLiveBytes() {
        return liveBytes;
    }

    public synchronized long getTotalAllocatedBytes() {
        return totalAllocatedBytes;
    }

    public synchronized long getFreePointer() {
        return freePointer;
    }
}