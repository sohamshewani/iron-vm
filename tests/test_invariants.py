package com.ironvm.test;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive Unit, Invariant, and Concurrent Stress-Test Suite for 'iron-vm'.
 * 
 * This file contains a complete, self-contained reference implementation of the IronVM
 * architecture along with a rigorous testing suite covering:
 * - Opcodes, boundary conditions, arithmetic edge cases (overflow/underflow, division by zero).
 * - Memory protection, bounds checking, stack underflow/overflow invariants.
 * - Multi-threaded concurrent execution, race condition detection, stress/fuzzing execution.
 * - State preservation, snapshotting, serialization recovery, and fault injection/resilience.
 */
public class IronVMExtensiveTestSuite {

    // =========================================================================
    // IRON-VM REFERENCE SPECIFICATION IMPLEMENTATION
    // =========================================================================

    public static final class Opcode {
        public static final byte HALT  = 0x00;
        public static final byte PUSH  = 0x01;
        public static final byte POP   = 0x02;
        public static final byte ADD   = 0x03;
        public static final byte SUB   = 0x04;
        public static final byte MUL   = 0x05;
        public static final byte DIV   = 0x06;
        public static final byte MOD   = 0x07;
        public static final byte LOAD  = 0x08;
        public static final byte STORE = 0x09;
        public static final byte JMP   = 0x0A;
        public static final byte JZ    = 0x0B;
        public static final byte JNZ   = 0x0C;
        public static final byte DUP   = 0x0D;
        public static final byte SWAP  = 0x0E;
        public static final byte CMP   = 0x0F;
        public static final byte NOP   = 0x10;
        public static final byte ATOMIC_INC = 0x11;
    }

    public static final class VMException extends RuntimeException {
        public VMException(String message) { super(message); }
    }

    public static final class VMState implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public long[] registers = new long[8];
        public long[] stack = new long[1024];
        public int sp = -1;
        public int pc = 0;
        public byte[] memory = new byte[65536];
        public boolean halted = false;
        public boolean fault = false;
        public String faultReason = "";
        public long executionCycles = 0;

        public VMState deepCopy() {
            VMState copy = new VMState();
            System.arraycopy(this.registers, 0, copy.registers, 0, this.registers.length);
            System.arraycopy(this.stack, 0, copy.stack, 0, this.stack.length);
            copy.sp = this.sp;
            copy.pc = this.pc;
            System.arraycopy(this.memory, 0, copy.memory, 0, this.memory.length);
            copy.halted = this.halted;
            copy.fault = this.fault;
            copy.faultReason = this.faultReason;
            copy.executionCycles = this.executionCycles;
            return copy;
        }

        public byte[] serialize() {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(this);
                return baos.toByteArray();
            } catch (IOException e) {
                throw new VMException("Serialization failed: " + e.getMessage());
            }
        }

        public static VMState deserialize(byte[] data) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                 ObjectInputStream ois = new ObjectInputStream(bais)) {
                return (VMState) ois.readObject();
            } catch (Exception e) {
                throw new VMException("Deserialization failed: " + e.getMessage());
            }
        }
    }

    public static class IronVM {
        private final VMState state;
        private final ReentrantLock vmLock = new ReentrantLock();
        private byte[] bytecode;
        private final long maxCycles;
        private final List<Consumer<IronVM>> stepHooks = new CopyOnWriteArrayList<>();

        public IronVM(byte[] bytecode) {
            this(bytecode, 1_000_000);
        }

        public IronVM(byte[] bytecode, long maxCycles) {
            this.bytecode = bytecode != null ? bytecode : new byte[0];
            this.state = new VMState();
            this.maxCycles = maxCycles;
        }

        public void addStepHook(Consumer<IronVM> hook) {
            stepHooks.add(hook);
        }

        public VMState getState() {
            vmLock.lock();
            try {
                return state.deepCopy();
            } finally {
                vmLock.unlock();
            }
        }

        public void restoreState(VMState newState) {
            vmLock.lock();
            try {
                VMState copy = newState.deepCopy();
                System.arraycopy(copy.registers, 0, this.state.registers, 0, state.registers.length);
                System.arraycopy(copy.stack, 0, this.state.stack, 0, state.stack.length);
                this.state.sp = copy.sp;
                this.state.pc = copy.pc;
                System.arraycopy(copy.memory, 0, this.state.memory, 0, state.memory.length);
                this.state.halted = copy.halted;
                this.state.fault = copy.fault;
                this.state.faultReason = copy.faultReason;
                this.state.executionCycles = copy.executionCycles;
            } finally {
                vmLock.unlock();
            }
        }

        public void execute() {
            while (!isTerminated()) {
                step();
            }
        }

        public boolean isTerminated() {
            vmLock.lock();
            try {
                return state.halted || state.fault || state.executionCycles >= maxCycles || state.pc >= bytecode.length;
            } finally {
                vmLock.unlock();
            }
        }

        public void step() {
            vmLock.lock();
            try {
                if (state.halted || state.fault) return;
                if (state.executionCycles >= maxCycles) {
                    triggerFault("Max execution cycles exceeded");
                    return;
                }
                if (state.pc < 0 || state.pc >= bytecode.length) {
                    triggerFault("Instruction pointer out of bounds: " + state.pc);
                    return;
                }

                for (Consumer<IronVM> hook : stepHooks) {
                    try {
                        hook.accept(this);
                    } catch (Exception ignored) {}
                }

                byte op = bytecode[state.pc++];
                state.executionCycles++;

                switch (op) {
                    case Opcode.HALT:
                        state.halted = true;
                        break;
                    case Opcode.NOP:
                        break;
                    case Opcode.PUSH:
                        long val = fetchLong();
                        pushStack(val);
                        break;
                    case Opcode.POP:
                        popStack();
                        break;
                    case Opcode.ADD: {
                        long b = popStack();
                        long a = popStack();
                        pushStack(Math.addExact(a, b));
                        break;
                    }
                    case Opcode.SUB: {
                        long b = popStack();
                        long a = popStack();
                        pushStack(Math.subtractExact(a, b));
                        break;
                    }
                    case Opcode.MUL: {
                        long b = popStack();
                        long a = popStack();
                        pushStack(Math.multiplyExact(a, b));
                        break;
                    }
                    case Opcode.DIV: {
                        long b = popStack();
                        long a = popStack();
                        if (b == 0) throw new ArithmeticException("Division by zero");
                        if (a == Long.MIN_VALUE && b == -1) throw new ArithmeticException("Integer overflow");
                        pushStack(a / b);
                        break;
                    }
                    case Opcode.MOD: {
                        long b = popStack();
                        long a = popStack();
                        if (b == 0) throw new ArithmeticException("Modulo by zero");
                        pushStack(a % b);
                        break;
                    }
                    case Opcode.LOAD: {
                        int addr = (int) popStack();
                        checkMemoryBounds(addr, 8);
                        long loaded = ByteBuffer.wrap(state.memory, addr, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
                        pushStack(loaded);
                        break;
                    }
                    case Opcode.STORE: {
                        int addr = (int) popStack();
                        long v = popStack();
                        checkMemoryBounds(addr, 8);
                        ByteBuffer.wrap(state.memory, addr, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(v);
                        break;
                    }
                    case Opcode.JMP: {
                        int target = (int) fetchLong();
                        if (target < 0 || target > bytecode.length) {
                            throw new VMException("Invalid jump target: " + target);
                        }
                        state.pc = target;
                        break;
                    }
                    case Opcode.JZ: {
                        int target = (int) fetchLong();
                        long cond = popStack();
                        if (cond == 0) {
                            if (target < 0 || target > bytecode.length) {
                                throw new VMException("Invalid jump target: " + target);
                            }
                            state.pc = target;
                        }
                        break;
                    }
                    case Opcode.JNZ: {
                        int target = (int) fetchLong();
                        long cond = popStack();
                        if (cond != 0) {
                            if (target < 0 || target > bytecode.length) {
                                throw new VMException("Invalid jump target: " + target);
                            }
                            state.pc = target;
                        }
                        break;
                    }
                    case Opcode.DUP: {
                        if (state.sp < 0) throw new VMException("Stack underflow on DUP");
                        pushStack(state.stack[state.sp]);
                        break;
                    }
                    case Opcode.SWAP: {
                        if (state.sp < 1) throw new VMException("Stack underflow on SWAP");
                        long top = state.stack[state.sp];
                        state.stack[state.sp] = state.stack[state.sp - 1];
                        state.stack[state.sp - 1] = top;
                        break;
                    }
                    case Opcode.CMP: {
                        long b = popStack();
                        long a = popStack();
                        long res = Long.compare(a, b);
                        pushStack(res);
                        break;
                    }
                    case Opcode.ATOMIC_INC: {
                        int addr = (int) fetchLong();
                        checkMemoryBounds(addr, 8);
                        synchronized (state.memory) {
                            ByteBuffer buf = ByteBuffer.wrap(state.memory, addr, 8).order(ByteOrder.LITTLE_ENDIAN);
                            long current = buf.getLong(0);
                            long updated = current + 1;
                            buf.putLong(0, updated);
                        }
                        break;
                    }
                    default:
                        throw new VMException("Unknown opcode: 0x" + Integer.toHexString(op & 0xFF));
                }
            } catch (Exception e) {
                triggerFault(e.getMessage());
            } finally {
                vmLock.unlock();
            }
        }

        private long fetchLong() {
            if (state.pc + 8 > bytecode.length) {
                throw new VMException