package com.iron.vm.types;

import java.util.Objects;

public final class Value {
    private static final long SIGN_BIT = 0x8000000000000000L;
    private static final long QNAN     = 0x7FFC000000000000L;

    public static final long TAG_MASK    = 0x7FFF800000000000L;
    public static final long PAYLOAD_MASK= 0x00007FFFFFFFFFFFL;

    public static final long TAG_NUMBER   = 0x0000000000000000L;
    public static final long TAG_OBJECT   = 0x0001000000000000L;
    public static final long TAG_BOOLEAN  = 0x0002000000000000L;
    public static final long TAG_NIL      = 0x0003000000000000L;
    public static final long TAG_SYMBOL   = 0x0004000000000000L;

    public static final Value NIL = new Value(QNAN | TAG_NIL);
    public static final Value TRUE = new Value(QNAN | TAG_BOOLEAN | 1L);
    public static final Value FALSE = new Value(QNAN | TAG_BOOLEAN | 0L);

    private final long bits;

    private Value(long bits) {
        this.bits = bits;
    }

    public static Value ofNumber(double value) {
        long raw = Double.doubleToRawLongBits(value);
        if ((raw & QNAN) == QNAN) {
            raw = QNAN;
        }
        return new Value(raw);
    }

    public static Value ofObject(long address) {
        if ((address & ~PAYLOAD_MASK) != 0) {
            throw new IllegalArgumentException("Object address exceeds 47-bit payload limit: 0x" + Long.toHexString(address));
        }
        return new Value(QNAN | TAG_OBJECT | address);
    }

    public static Value ofBoolean(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static Value ofSymbol(int symbolId) {
        if ((symbolId & ~0xFFFFFFFFL) != 0 && (symbolId < 0)) {
            throw new IllegalArgumentException("Symbol ID out of bounds: " + symbolId);
        }
        return new Value(QNAN | TAG_SYMBOL | ((long) symbolId & PAYLOAD_MASK));
    }

    public static Value fromRawBits(long bits) {
        return new Value(bits);
    }

    public long getRawBits() {
        return bits;
    }

    public boolean isNumber() {
        return (bits & QNAN) != QNAN;
    }

    public boolean isObject() {
        return (bits & QNAN) == QNAN && (bits & TAG_MASK) == TAG_OBJECT;
    }

    public boolean isBoolean() {
        return (bits & QNAN) == QNAN && (bits & TAG_MASK) == TAG_BOOLEAN;
    }

    public boolean isNil() {
        return bits == (QNAN | TAG_NIL);
    }

    public boolean isSymbol() {
        return (bits & QNAN) == QNAN && (bits & TAG_MASK) == TAG_SYMBOL;
    }

    public double asNumber() {
        if (!isNumber()) {
            throw new ClassCastException("Value is not a Number: 0x" + Long.toHexString(bits));
        }
        return Double.longBitsToDouble(bits);
    }

    public long asObjectAddress() {
        if (!isObject()) {
            throw new ClassCastException("Value is not an Object reference: 0x" + Long.toHexString(bits));
        }
        return bits & PAYLOAD_MASK;
    }

    public boolean asBoolean() {
        if (!isBoolean()) {
            throw new ClassCastException("Value is not a Boolean: 0x" + Long.toHexString(bits));
        }
        return (bits & 1L) != 0L;
    }

    public int asSymbol() {
        if (!isSymbol()) {
            throw new ClassCastException("Value is not a Symbol ID: 0x" + Long.toHexString(bits));
        }
        return (int) (bits & PAYLOAD_MASK);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Value other = (Value) obj;
        return this.bits == other.bits;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(bits);
    }

    @Override
    public String toString() {
        if (isNumber()) {
            return Double.toString(asNumber());
        }
        if (isNil()) {
            return "nil";
        }
        if (isBoolean()) {
            return Boolean.toString(asBoolean());
        }
        if (isObject()) {
            return "Object(0x" + Long.toHexString(asObjectAddress()) + ")";
        }
        if (isSymbol()) {
            return "Symbol(" + asSymbol() + ")";
        }
        return "Unknown(0x" + Long.toHexString(bits) + ")";
    }
}