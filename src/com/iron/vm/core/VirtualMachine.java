package com.iron.vm.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

public final class VirtualMachine {

    public static final long TAG_MASK    = 0xFFFF000000000000L;
    public static final long PAYLOAD_MASK= 0x0000FFFFFFFFFFFFL;

    public static final long TAG_INT     = 0x0001000000000000L;
    public static final long TAG_FLOAT   = 0x0002000000000000L;
    public static final long TAG_BOOL    = 0x0003000000000000L;
    public static final long TAG_OBJECT  = 0x0004000000000000L;
    public static final long TAG_NIL     = 0x0005000000000000L;

    public static final int OP_NOP       = 0x00;
    public static final int OP_LOAD_CONST= 0x01;
    public static final int OP_MOVE      = 0x02;
    public static final int OP_ADD       = 0x03;
    public static final int OP_SUB       = 0x04;
    public static final int OP_MUL       = 0x05;
    public static final int OP_DIV       = 0x06;
    public static final int OP_JMP       = 0x07;
    public static final int OP_JMP_TRUE  = 0x08;
    public static final int OP_JMP_FALSE = 0x09;
    public static final int OP_CALL      = 0x0A;
    public static final int OP_RET       = 0x0B;
    public static final int OP_ALLOC     = 0x0C;
    public static final int OP_PRINT     = 0x0D;
    public static final int OP_HALT      = 0x0E;

    public static final int STATE_RUNNING   = 0;
    public static final int STATE_SAFEPOINT = 1;

    private final long[] registers;
    private final int[] callStackIP;
    private final int[] callStackFP;
    private int callStackTop;

    private final ByteBuffer heapMemory;
    private int heapFreePointer;
    private final int heapCapacity;

    private byte[] bytecode;
    private int instructionPointer;

    private final AtomicInteger safepointState;
    private boolean isRunning;

    public VirtualMachine(int registerCount, int callStackDepth, int heapSizeInBytes) {
        if (registerCount <= 0 || callStackDepth <= 0 || heapSizeInBytes <= 0) {
            throw new IllegalArgumentException("Invalid VM resource sizing parameters.");
        }
        this.registers = new long[registerCount];
        this.callStackIP = new int[callStackDepth];
        this.callStackFP = new int[callStackDepth];
        this.callStackTop = 0;

        this.heapCapacity = heapSizeInBytes;
        this.heapMemory = ByteBuffer.allocateDirect(heapSizeInBytes);
        this.heapMemory.order(ByteOrder.LITTLE_ENDIAN);
        this.heapFreePointer = 0;

        this.bytecode = new byte[0];
        this.instructionPointer = 0;

        this.safepointState = new AtomicInteger(STATE_RUNNING);
        this.isRunning = false;
    }

    public void loadProgram(byte[] compiledBytecode) {
        if (compiledBytecode == null) {
            throw new NullPointerException("Bytecode stream cannot be null.");
        }
        this.bytecode = compiledBytecode;
        this.instructionPointer = 0;
    }

    public void execute() {
        this.isRunning = true;
        final byte[] code = this.bytecode;
        final long[] regs = this.registers;
        int ip = this.instructionPointer;

        try {
            dispatch:
            while (this.isRunning) {
                if (safepointState.get() == STATE_SAFEPOINT) {
                    cooperativeSafepoint();
                }

                if (ip < 0 || ip >= code.length) {
                    throw new VMExecutionException("Instruction pointer out of bounds: " + ip);
                }

                int opcode = code[ip++] & 0xFF;

                switch (opcode) {
                    case OP_NOP:
                        break;

                    case OP_LOAD_CONST: {
                        int regDst = code[ip++] & 0xFF;
                        long rawVal = readLong(code, ip);
                        ip += 8;
                        regs[regDst] = rawVal;
                        break;
                    }

                    case OP_MOVE: {
                        int regDst = code[ip++] & 0xFF;
                        int regSrc = code[ip++] & 0xFF;
                        regs[regDst] = regs[regSrc];
                        break;
                    }

                    case OP_ADD: {
                        int regDst = code[ip++] & 0xFF;
                        int regA = code[ip++] & 0xFF;
                        int regB = code[ip++] & 0xFF;
                        regs[regDst] = executeAdd(regs[regA], regs[regB]);
                        break;
                    }

                    case OP_SUB: {
                        int regDst = code[ip++] & 0xFF;
                        int regA = code[ip++] & 0xFF;
                        int regB = code[ip++] & 0xFF;
                        regs[regDst] = executeSub(regs[regA], regs[regB]);
                        break;
                    }

                    case OP_MUL: {
                        int regDst = code[ip++] & 0xFF;
                        int regA = code[ip++] & 0xFF;
                        int regB = code[ip++] & 0xFF;
                        regs[regDst] = executeMul(regs[regA], regs[regB]);
                        break;
                    }

                    case OP_DIV: {
                        int regDst = code[ip++] & 0xFF;
                        int regA = code[ip++] & 0xFF;
                        int regB = code[ip++] & 0xFF;
                        regs[regDst] = executeDiv(regs[regA], regs[regB]);
                        break;
                    }

                    case OP_JMP: {
                        int targetIP = readInt(code, ip);
                        ip = targetIP;
                        break;
                    }

                    case OP_JMP_TRUE: {
                        int regCond = code[ip++] & 0xFF;
                        int targetIP = readInt(code, ip);
                        ip += 4;
                        if (isTruthy(regs[regCond])) {
                            ip = targetIP;
                        }
                        break;
                    }

                    case OP_JMP_FALSE: {
                        int regCond = code[ip++] & 0xFF;
                        int targetIP = readInt(code, ip);
                        ip += 4;
                        if (!isTruthy(regs[regCond])) {
                            ip = targetIP;
                        }
                        break;
                    }

                    case OP_CALL: {
                        int targetIP = readInt(code, ip);
                        ip += 4;
                        if (callStackTop >= callStackIP.length) {
                            throw new VMPanicException("Call stack overflow.");
                        }
                        callStackIP[callStackTop] = ip;
                        callStackFP[callStackTop] = 0;
                        callStackTop++;
                        ip = targetIP;
                        break;
                    }

                    case OP_RET: {
                        if (callStackTop <= 0) {
                            throw new VMPanicException("Call stack underflow.");
                        }
                        callStackTop--;
                        ip = callStackIP[callStackTop];
                        break;
                    }

                    case OP_ALLOC: {
                        int regDst = code[ip++] & 0xFF;
                        int size = readInt(code, ip);
                        ip += 4;
                        long objAddr = allocateOnHeap(size);
                        regs[regDst] = TAG_OBJECT | (objAddr & PAYLOAD_MASK);
                        break;
                    }

                    case OP_PRINT: {
                        int regSrc = code[ip++] & 0xFF;
                        printValue(regs[regSrc]);
                        break;
                    }

                    case OP_HALT: {
                        this.isRunning = false;
                        break dispatch;
                    }

                    default:
                        throw new VMExecutionException("Unknown opcode encountered: 0x" + Integer.toHexString(opcode));
                }
            }
        } finally {
            this.instructionPointer = ip;
        }
    }

    private long executeAdd(long valA, long valB) {
        long tagA = valA & TAG_MASK;
        long tagB = valB & TAG_MASK;
        if (tagA == TAG_INT && tagB == TAG_INT) {
            long a = valA & PAYLOAD_MASK;
            long b = valB & PAYLOAD_MASK;
            return TAG_INT | ((a + b) & PAYLOAD_MASK);
        } else if (tagA == TAG_FLOAT && tagB == TAG_FLOAT) {
            double a = Double.longBitsToDouble(valA & PAYLOAD_MASK);
            double b = Double.longBitsToDouble(valB & PAYLOAD_MASK);
            long resBits = Double.doubleToRawLongBits(a + b);
            return TAG_FLOAT | (resBits & PAYLOAD_MASK);
        }
        throw new VMTypeException("Type mismatch in ADD instruction.");
    }

    private long executeSub(long valA, long valB) {
        long tagA = valA & TAG_MASK;
        long tagB = valB & TAG_MASK;
        if (tagA == TAG_INT && tagB == TAG_INT) {
            long a = valA & PAYLOAD_MASK;
            long b = valB & PAYLOAD_MASK;
            return TAG_INT | ((a - b) & PAYLOAD_MASK);
        } else if (tagA == TAG_FLOAT && tagB == TAG_FLOAT) {
            double a = Double.longBitsToDouble(valA & PAYLOAD_MASK);
            double b = Double.longBitsToDouble(valB & PAYLOAD_MASK);
            long resBits = Double.doubleToRawLongBits(a - b);
            return TAG_FLOAT | (resBits & PAYLOAD_MASK);
        }
        throw new VMTypeException("Type mismatch in SUB instruction.");
    }

    private long executeMul(long valA, long valB) {
        long tagA = valA & TAG_MASK;
        long tagB = valB & TAG_MASK;
        if (tagA == TAG_INT && tagB == TAG_INT) {
            long a = valA & PAYLOAD_MASK;
            long b = valB & PAYLOAD_MASK;
            return TAG_INT | ((a * b) & PAYLOAD_MASK);
        } else if (tagA == TAG_FLOAT && tagB == TAG_FLOAT) {
            double a = Double.longBitsToDouble(valA & PAYLOAD_MASK);
            double b = Double.longBitsToDouble(valB & PAYLOAD_MASK);
            long resBits = Double.doubleToRawLongBits(a * b);
            return TAG_FLOAT | (resBits & PAYLOAD_MASK);
        }
        throw new VMTypeException("Type mismatch in MUL instruction.");
    }

    private long executeDiv(long valA, long valB) {
        long tagA = valA & TAG_MASK;
        long tagB = valB & TAG_MASK;
        if (tagA == TAG_INT && tagB == TAG_INT) {
            long a = valA & PAYLOAD_MASK;
            long b = valB & PAYLOAD_MASK;
            if (b == 0) {
                throw new VMExecutionException("Division by zero.");
            }
            return TAG_INT | ((a / b) & PAYLOAD_MASK);
        } else if (tagA == TAG_FLOAT && tagB == TAG_FLOAT) {
            double a = Double.longBitsToDouble(valA & PAYLOAD_MASK);
            double b = Double.longBitsToDouble(valB & PAYLOAD_MASK);
            if (b == 0.0) {
                throw new VMExecutionException("Floating-point division by zero.");
            }
            long resBits = Double.doubleToRawLongBits(a / b);
            return TAG_FLOAT | (resBits & PAYLOAD_MASK);
        }
        throw new VMTypeException("Type mismatch in DIV instruction.");
    }

    private boolean isTruthy(long val) {
        long tag = val & TAG_MASK;
        if (tag == TAG_BOOL || tag == TAG_INT) {
            return (val & PAYLOAD_MASK) != 0;
        } else if (tag == TAG_NIL) {
            return false;
        }
        return true;
    }

    private long allocateOnHeap(int size) {
        int alignedSize = (size + 7) & ~7;
        int headerSize = 16;
        int totalAllocation = alignedSize + headerSize;

        if (heapFreePointer + totalAllocation > heapCapacity) {
            triggerSynchronousGC();
            if (heapFreePointer + totalAllocation > heapCapacity) {
                throw new OutOfMemoryError("VM Heap exhausted. OOM triggered.");
            }
        }

        int address = heapFreePointer;
        heapMemory.position(address);
        heapMemory.putLong(0L);
        heapMemory.putInt(alignedSize);
        heapMemory.putInt(0);

        heapFreePointer += totalAllocation;
        return address + headerSize;
    }

    private void triggerSynchronousGC() {
        markPhase();
        sweepPhase();
    }

    private void markPhase() {
        for (int i = 0; i < registers.length; i++) {
            long reg = registers[i];
            if ((reg & TAG_MASK) == TAG_OBJECT) {
                int objAddr = (int)(reg & PAYLOAD_MASK);
                markObject(objAddr);
            }
        }
    }

    private void markObject(int objAddr) {
        int headerAddr = objAddr - 16;
        if (headerAddr < 0 || headerAddr >= heapCapacity) return;
        heapMemory.position(headerAddr);
        long markBitAndAge = heapMemory.getLong();
        if ((markBitAndAge & 1L) == 0L) {
            long newHeader = markBitAndAge | 1L;
            heapMemory.putLong(headerAddr, newHeader);
        }
    }

    private void sweepPhase() {
        int current = 0;
        int newFreePointer = 0;

        while (current < heapFreePointer) {
            heapMemory.position(current);
            long markBitAndAge = heapMemory.getLong();
            int blockSize = heapMemory.getInt();
            int gcFlags = heapMemory.getInt();

            int totalBlockSize = blockSize + 16;
            boolean isMarked = (markBitAndAge & 1L) != 0L;

            if (isMarked) {
                long clearedHeader = markBitAndAge & ~1L;
                heapMemory.putLong(current, clearedHeader);
                current += totalBlockSize;
                newFreePointer = current;
            } else {
                current += totalBlockSize;
            }
        }
        heapFreePointer = newFreePointer;
    }

    private void cooperativeSafepoint() {
        synchronized (this) {
            while (safepointState.get() == STATE_SAFEPOINT) {
                try {
                    this.wait(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new VMPanicException("Thread interrupted during VM safepoint coordination.");
                }
            }
        }
    }

    public void requestSafepoint() {
        safepointState.set(STATE_SAFEPOINT);
    }

    public void resumeFromSafepoint() {
        safepointState.set(STATE_RUNNING);
        synchronized (this) {
            this.notifyAll();
        }
    }

    private long readLong(byte[] code, int index) {
        return (((long)code[index] & 0xFF)) |
               (((long)code[index+1] & 0xFF) << 8) |
               (((long)code[index+2] & 0xFF) << 16) |
               (((long)code[index+3] & 0xFF) << 24) |
               (((long)code[index+