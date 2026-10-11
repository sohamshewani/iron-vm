package com.iron.vm.core;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BytecodeCompiler for the 'iron-vm' runtime system.
 * Translates a strongly-typed Abstract Syntax Tree into a dense, fixed-width
 * instruction stream of opcodes and operands, optimized for register allocation
 * and jump-target backpatching.
 */
public final class BytecodeCompiler {

    public static final class CompiledProgram {
        private final byte[] bytecode;
        private final Object[] constants;
        private final int entryPoint;

        public CompiledProgram(byte[] bytecode, Object[] constants, int entryPoint) {
            this.bytecode = bytecode;
            this.constants = constants;
            this.entryPoint = entryPoint;
        }

        public byte[] getBytecode() {
            return bytecode;
        }

        public Object[] getConstants() {
            return constants;
        }

        public int getEntryPoint() {
            return entryPoint;
        }

        public ByteBuffer serialize() throws IOException {
            int totalSize = 4 + 4 + bytecode.length + 4 + 4;
            for (Object constant : constants) {
                if (constant instanceof Long) {
                    totalSize += 1 + 8;
                } else if (constant instanceof Double) {
                    totalSize += 1 + 8;
                } else if (constant instanceof String) {
                    byte[] strBytes = ((String) constant).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    totalSize += 1 + 4 + strBytes.length;
                } else {
                    throw new IOException("Unsupported constant type: " + constant.getClass());
                }
            }

            ByteBuffer buffer = ByteBuffer.allocate(totalSize);
            buffer.order(ByteOrder.BIG_ENDIAN);
            
            buffer.putInt(0x49524F4E); // Magic header "IRON"
            buffer.putInt(entryPoint);
            
            buffer.putInt(bytecode.length);
            buffer.put(bytecode);
            
            buffer.putInt(constants.length);
            for (Object constant : constants) {
                if (constant instanceof Long) {
                    buffer.put((byte) 1);
                    buffer.putLong((Long) constant);
                } else if (constant instanceof Double) {
                    buffer.put((byte) 2);
                    buffer.putDouble((Double) constant);
                } else if (constant instanceof String) {
                    buffer.put((byte) 3);
                    byte[] strBytes = ((String) constant).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    buffer.putInt(strBytes.length);
                    buffer.put(strBytes);
                }
            }
            
            buffer.flip();
            return buffer;
        }
    }

    public interface ASTNode {
        void accept(ASTVisitor visitor);
    }

    public static final class ProgramNode implements ASTNode {
        private final List<ASTNode> statements;

        public ProgramNode(List<ASTNode> statements) {
            this.statements = statements;
        }

        public List<ASTNode> getStatements() {
            return statements;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class FunctionNode implements ASTNode {
        private final String name;
        private final List<String> parameters;
        private final List<ASTNode> body;

        public FunctionNode(String name, List<String> parameters, List<ASTNode> body) {
            this.name = name;
            this.parameters = parameters;
            this.body = body;
        }

        public String getName() {
            return name;
        }

        public List<String> getParameters() {
            return parameters;
        }

        public List<ASTNode> getBody() {
            return body;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class VarDeclNode implements ASTNode {
        private final String name;
        private final ASTNode initializer;

        public VarDeclNode(String name, ASTNode initializer) {
            this.name = name;
            this.initializer = initializer;
        }

        public String getName() {
            return name;
        }

        public ASTNode getInitializer() {
            return initializer;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class AssignNode implements ASTNode {
        private final String name;
        private final ASTNode value;

        public AssignNode(String name, ASTNode value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public ASTNode getValue() {
            return value;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class ReturnNode implements ASTNode {
        private final ASTNode value;

        public ReturnNode(ASTNode value) {
            this.value = value;
        }

        public ASTNode getValue() {
            return value;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class IfNode implements ASTNode {
        private final ASTNode condition;
        private final List<ASTNode> thenBranch;
        private final List<ASTNode> elseBranch;

        public IfNode(ASTNode condition, List<ASTNode> thenBranch, List<ASTNode> elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        public ASTNode getCondition() {
            return condition;
        }

        public List<ASTNode> getThenBranch() {
            return thenBranch;
        }

        public List<ASTNode> getElseBranch() {
            return elseBranch;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class WhileNode implements ASTNode {
        private final ASTNode condition;
        private final List<ASTNode> body;

        public WhileNode(ASTNode condition, List<ASTNode> body) {
            this.condition = condition;
            this.body = body;
        }

        public ASTNode getCondition() {
            return condition;
        }

        public List<ASTNode> getBody() {
            return body;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class BinaryNode implements ASTNode {
        private final String operator;
        private final ASTNode left;
        private final ASTNode right;

        public BinaryNode(String operator, ASTNode left, ASTNode right) {
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        public String getOperator() {
            return operator;
        }

        public ASTNode getLeft() {
            return left;
        }

        public ASTNode getRight() {
            return right;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class UnaryNode implements ASTNode {
        private final String operator;
        private final ASTNode operand;

        public UnaryNode(String operator, ASTNode operand) {
            this.operator = operator;
            this.operand = operand;
        }

        public String getOperator() {
            return operator;
        }

        public ASTNode getOperand() {
            return operand;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class CallNode implements ASTNode {
        private final String callee;
        private final List<ASTNode> arguments;

        public CallNode(String callee, List<ASTNode> arguments) {
            this.callee = callee;
            this.arguments = arguments;
        }

        public String getCallee() {
            return callee;
        }

        public List<ASTNode> getArguments() {
            return arguments;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class LiteralNode implements ASTNode {
        private final Object value;

        public LiteralNode(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static final class VariableNode implements ASTNode {
        private final String name;

        public VariableNode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public void accept(ASTVisitor visitor) {
            visitor.visit(this);
        }
    }

    public interface ASTVisitor {
        void visit(ProgramNode node);
        void visit(FunctionNode node);
        void visit(VarDeclNode node);
        void visit(AssignNode node);
        void visit(ReturnNode node);
        void visit(IfNode node);
        void visit(WhileNode node);
        void visit(BinaryNode node);
        void visit(UnaryNode node);
        void visit(CallNode node);
        void visit(LiteralNode node);
        void visit(VariableNode node);
    }

    public static final class CompilerException extends RuntimeException {
        public CompilerException(String message) {
            super(message);
        }
    }

    private static final class SymbolTable {
        private final Map<String, Integer> symbols = new HashMap<>();
        private final SymbolTable parent;
        private int nextSlot = 0;

        public SymbolTable(SymbolTable parent) {
            this.parent = parent;
            this.nextSlot = (parent != null) ? parent.nextSlot : 0;
        }

        public int define(String name) {
            if (symbols.containsKey(name)) {
                throw new CompilerException("Symbol already defined in scope: " + name);
            }
            int slot = nextSlot++;
            symbols.put(name, slot);
            return slot;
        }

        public Integer resolve(String name) {
            Integer slot = symbols.get(name);
            if (slot != null) {
                return slot;
            }
            if (parent != null) {
                return parent.resolve(name);
            }
            return null;
        }

        public int getNextSlot() {
            return nextSlot;
        }
    }

    private static final class FunctionInfo {
        private final String name;
        private final int address;
        private final int arity;

        public FunctionInfo(String name, int address, int arity) {
            this.name = name;
            this.address = address;
            this.arity = arity;
        }

        public String getName() {
            return name;
        }

        public int getAddress() {
            return address;
        }

        public int getArity() {
            return arity;
        }
    }

    private static final class PatchLocation {
        private final int position;
        private final boolean isLongJump;

        public PatchLocation(int position, boolean isLongJump) {
            this.position = position;
            this.isLongJump = isLongJump;
        }

        public int getPosition() {
            return position;
        }

        public boolean isLongJump() {
            return isLongJump;
        }
    }

    public static CompiledProgram compile(ProgramNode root) {
        BytecodeCompilerInstance compiler = new BytecodeCompilerInstance();
        return compiler.compileProgram(root);
    }

    private static final class BytecodeCompilerInstance implements ASTVisitor {
        private final ByteBuffer codeBuffer = ByteBuffer.allocate(65536);
        private final List<Object> constants = new ArrayList<>();
        private final Map<String, FunctionInfo> functions = new HashMap<>();
        
        private SymbolTable currentScope = new SymbolTable(null);
        private int currentRegisterAllocation = 0;
        private int entryPoint = -1;

        public CompiledProgram compileProgram(ProgramNode root) {
            codeBuffer.order(ByteOrder.BIG_ENDIAN);
            
            // Emit bootstrap jump to main entry point
            emitOpcode((byte) Opcode.JUMP.ordinal());
            int entryJumpPatch = codeBuffer.position();
            emitInt(0);

            root.accept(this);

            FunctionInfo mainFunc = functions.get("main");
            if (mainFunc == null) {
                throw new CompilerException("Compilation error: Missing 'main' function entry point.");
            }

            int mainAddress = mainFunc.getAddress();
            int currentPos = codeBuffer.position();
            codeBuffer.position(entryJumpPatch);
            codeBuffer.putInt(mainAddress);
            codeBuffer.position(currentPos);

            emitOpcode((byte) Opcode.HALT.ordinal());

            byte[] bytecode = new byte[codeBuffer.position()];
            System.arraycopy(codeBuffer.array(), 0, bytecode, 0, bytecode.length);

            return new CompiledProgram(bytecode, constants.toArray(), 0);
        }

        private void emitOpcode(byte opcode) {
            ensureCapacity(1);
            codeBuffer.put(opcode);
        }

        private void emitByte(byte b) {
            ensureCapacity(1);
            codeBuffer.put(b);
        }

        private void emitShort(short s) {
            ensureCapacity(2);
            codeBuffer.putShort(s);
        }

        private void emitInt(int i) {
            ensureCapacity(4);
            codeBuffer.putInt(i);
        }

        private void emitLong(long l) {
            ensureCapacity(8);
            codeBuffer.putLong(l);
        }

        private void ensureCapacity(int needed) {
            if (codeBuffer.remaining() < needed) {
                int newCapacity = codeBuffer.capacity() * 2 + needed;
                ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
                newBuffer.order(ByteOrder.BIG_ENDIAN);
                codeBuffer.flip();
                newBuffer.put(codeBuffer);
                codeBuffer.clear();
                codeBuffer.put(newBuffer);
                codeBuffer.flip();
                codeBuffer.position(newBuffer.position());
            }
        }

        private int addConstant(Object value) {
            for (int i = 0; i < constants.size(); i++) {
                if (constants.get(i).equals(value)) {
                    return i;
                }
            }
            constants.add(value);
            return constants.size() - 1;
        }

        @Override
        public void visit(ProgramNode node) {
            for (ASTNode stmt : node.getStatements()) {
                stmt.accept(this);
            }
        }

        @Override
        public void visit(FunctionNode node) {
            int funcAddress = codeBuffer.position();
            functions.put(node.getName(), new FunctionInfo(node.getName(), funcAddress, node.getParameters().size()));

            SymbolTable previousScope = currentScope;
            currentScope = new SymbolTable(previousScope);
            
            for (String param : node.getParameters()) {
                currentScope.define(param);
            }

            int startReg = currentScope.getNextSlot();
            currentRegisterAllocation = startReg;

            for (ASTNode stmt : node.getBody()) {
                stmt.accept(this);
            }

            // Implicit return null if no explicit return
            emitOpcode((byte) Opcode.LOAD_CONST.ordinal());
            int nilConst = addConstant(0L);
            emitInt(nilConst);
            emitInt(0); // target reg 0
            emitOpcode((byte) Opcode.RET.ordinal());

            currentScope = previousScope;
        }

        @Override
        public void visit(VarDeclNode node) {
            int slot = currentScope.define(node.getName());
            if (node.getInitializer() != null) {
                node.getInitializer().accept(this);
                // Assuming result lands in register 0 or we move it
                emitOpcode((byte) Opcode.MOVE.ordinal());
                emitInt(0); // source
                emitInt(slot); // destination
            }
        }