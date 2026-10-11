package com.iron.vm.core;

import java.util.ArrayList;
import java.util.List;

public final class ASTParser {

    public enum TokenType {
        TOKEN_EOF,
        TOKEN_IDENTIFIER,
        TOKEN_NUMBER,
        TOKEN_STRING,
        TOKEN_LET,
        TOKEN_FN,
        TOKEN_IF,
        TOKEN_ELSE,
        TOKEN_WHILE,
        TOKEN_RETURN,
        TOKEN_PLUS,
        TOKEN_MINUS,
        TOKEN_STAR,
        TOKEN_SLASH,
        TOKEN_ASSIGN,
        TOKEN_EQ,
        TOKEN_NE,
        TOKEN_LT,
        TOKEN_LE,
        TOKEN_GT,
        TOKEN_GE,
        TOKEN_LPAREN,
        TOKEN_RPAREN,
        TOKEN_LBRACE,
        TOKEN_RBRACE,
        TOKEN_COMMA,
        TOKEN_SEMICOLON
    }

    public static final class Token {
        public final TokenType type;
        public final String lexeme;
        public final int line;
        public final int column;
        public final int start;
        public final int end;

        public Token(TokenType type, String lexeme, int line, int column, int start, int end) {
            this.type = type;
            this.lexeme = lexeme;
            this.line = line;
            this.column = column;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return String.format("Token[%s, '%s', line=%d, col=%d]", type, lexeme, line, column);
        }
    }

    public static final class Lexer {
        private final String source;
        private final int length;
        private int cursor = 0;
        private int line = 1;
        private int column = 1;

        public Lexer(String source) {
            this.source = source != null ? source : "";
            this.length = this.source.length();
        }

        public List<Token> tokenize() {
            List<Token> tokens = new ArrayList<>();
            while (cursor < length) {
                char c = source.charAt(cursor);
                if (c == '\n') {
                    line++;
                    column = 1;
                    cursor++;
                    continue;
                }
                if (Character.isWhitespace(c)) {
                    cursor++;
                    column++;
                    continue;
                }

                int startPos = cursor;
                int startCol = column;

                if (Character.isLetter(c) || c == '_') {
                    while (cursor < length) {
                        char next = source.charAt(cursor);
                        if (Character.isLetterOrDigit(next) || next == '_') {
                            cursor++;
                            column++;
                        } else {
                            break;
                        }
                    }
                    String lexeme = source.substring(startPos, cursor);
                    TokenType type = TokenType.TOKEN_IDENTIFIER;
                    switch (lexeme) {
                        case "let": type = TokenType.TOKEN_LET; break;
                        case "fn": type = TokenType.TOKEN_FN; break;
                        case "if": type = TokenType.TOKEN_IF; break;
                        case "else": type = TokenType.TOKEN_ELSE; break;
                        case "while": type = TokenType.TOKEN_WHILE; break;
                        case "return": type = TokenType.TOKEN_RETURN; break;
                        default: type = TokenType.TOKEN_IDENTIFIER; break;
                    }
                    tokens.add(new Token(type, lexeme, line, startCol, startPos, cursor));
                    continue;
                }

                if (Character.isDigit(c)) {
                    while (cursor < length) {
                        char next = source.charAt(cursor);
                        if (Character.isDigit(next) || next == '.') {
                            cursor++;
                            column++;
                        } else {
                            break;
                        }
                    }
                    String lexeme = source.substring(startPos, cursor);
                    tokens.add(new Token(TokenType.TOKEN_NUMBER, lexeme, line, startCol, startPos, cursor));
                    continue;
                }

                if (c == '"') {
                    cursor++;
                    column++;
                    int contentStart = cursor;
                    while (cursor < length && source.charAt(cursor) != '"') {
                        if (source.charAt(cursor) == '\n') {
                            line++;
                            column = 1;
                        } else {
                            column++;
                        }
                        cursor++;
                    }
                    if (cursor >= length) {
                        throw new SyntaxError("Unterminated string literal", line, startCol);
                    }
                    String lexeme = source.substring(contentStart, cursor);
                    cursor++; // consume closing quote
                    column++;
                    tokens.add(new Token(TokenType.TOKEN_STRING, lexeme, line, startCol, startPos, cursor));
                    continue;
                }

                // Operators and punctuation
                cursor++;
                column++;
                switch (c) {
                    case '+': tokens.add(new Token(TokenType.TOKEN_PLUS, "+", line, startCol, startPos, cursor)); break;
                    case '-': tokens.add(new Token(TokenType.TOKEN_MINUS, "-", line, startCol, startPos, cursor)); break;
                    case '*': tokens.add(new Token(TokenType.TOKEN_STAR, "*", line, startCol, startPos, cursor)); break;
                    case '/': 
                        // Check for comments
                        if (cursor < length && source.charAt(cursor) == '/') {
                            while (cursor < length && source.charAt(cursor) != '\n') {
                                cursor++;
                            }
                            break;
                        }
                        tokens.add(new Token(TokenType.TOKEN_SLASH, "/", line, startCol, startPos, cursor)); 
                        break;
                    case '=':
                        if (cursor < length && source.charAt(cursor) == '=') {
                            cursor++;
                            column++;
                            tokens.add(new Token(TokenType.TOKEN_EQ, "==", line, startCol, startPos, cursor));
                        } else {
                            tokens.add(new Token(TokenType.TOKEN_ASSIGN, "=", line, startCol, startPos, cursor));
                        }
                        break;
                    case '!':
                        if (cursor < length && source.charAt(cursor) == '=') {
                            cursor++;
                            column++;
                            tokens.add(new Token(TokenType.TOKEN_NE, "!=", line, startCol, startPos, cursor));
                        } else {
                            throw new SyntaxError("Unexpected character '!'", line, startCol);
                        }
                        break;
                    case '<':
                        if (cursor < length && source.charAt(cursor) == '=') {
                            cursor++;
                            column++;
                            tokens.add(new Token(TokenType.TOKEN_LE, "<=", line, startCol, startPos, cursor));
                        } else {
                            tokens.add(new Token(TokenType.TOKEN_LT, "<", line, startCol, startPos, cursor));
                        }
                        break;
                    case '>':
                        if (cursor < length && source.charAt(cursor) == '=') {
                            cursor++;
                            column++;
                            tokens.add(new Token(TokenType.TOKEN_GE, ">=", line, startCol, startPos, cursor));
                        } else {
                            tokens.add(new Token(TokenType.TOKEN_GT, ">", line, startCol, startPos, cursor));
                        }
                        break;
                    case '(': tokens.add(new Token(TokenType.TOKEN_LPAREN, "(", line, startCol, startPos, cursor)); break;
                    case ')': tokens.add(new Token(TokenType.TOKEN_RPAREN, ")", line, startCol, startPos, cursor)); break;
                    case '{': tokens.add(new Token(TokenType.TOKEN_LBRACE, "{", line, startCol, startPos, cursor)); break;
                    case '}': tokens.add(new Token(TokenType.TOKEN_RBRACE, "}", line, startCol, startPos, cursor)); break;
                    case ',': tokens.add(new Token(TokenType.TOKEN_COMMA, ",", line, startCol, startPos, cursor)); break;
                    case ';': tokens.add(new Token(TokenType.TOKEN_SEMICOLON, ";", line, startCol, startPos, cursor)); break;
                    default:
                        throw new SyntaxError(String.format("Unexpected character '%c'", c), line, startCol);
                }
            }
            tokens.add(new Token(TokenType.TOKEN_EOF, "", line, column, cursor, cursor));
            return tokens;
        }
    }

    public static abstract class ASTNode {
        public final int start;
        public final int end;

        protected ASTNode(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public abstract <R> R accept(ASTVisitor<R> visitor);
    }

    public static final class ProgramNode extends ASTNode {
        public final List<ASTNode> declarations;

        public ProgramNode(List<ASTNode> declarations, int start, int end) {
            super(start, end);
            this.declarations = declarations;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitProgram(this);
        }
    }

    public static final class FunctionDeclNode extends ASTNode {
        public final String name;
        public final List<String> parameters;
        public final BlockNode body;

        public FunctionDeclNode(String name, List<String> parameters, BlockNode body, int start, int end) {
            super(start, end);
            this.name = name;
            this.parameters = parameters;
            this.body = body;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitFunctionDecl(this);
        }
    }

    public static final class VarDeclNode extends ASTNode {
        public final String name;
        public final ASTNode initializer;

        public VarDeclNode(String name, ASTNode initializer, int start, int end) {
            super(start, end);
            this.name = name;
            this.initializer = initializer;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitVarDecl(this);
        }
    }

    public static final class AssignmentNode extends ASTNode {
        public final String name;
        public final ASTNode value;

        public AssignmentNode(String name, ASTNode value, int start, int end) {
            super(start, end);
            this.name = name;
            this.value = value;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitAssignment(this);
        }
    }

    public static final class BlockNode extends ASTNode {
        public final List<ASTNode> statements;

        public BlockNode(List<ASTNode> statements, int start, int end) {
            super(start, end);
            this.statements = statements;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitBlock(this);
        }
    }

    public static final class IfNode extends ASTNode {
        public final ASTNode condition;
        public final ASTNode thenBranch;
        public final ASTNode elseBranch;

        public IfNode(ASTNode condition, ASTNode thenBranch, ASTNode elseBranch, int start, int end) {
            super(start, end);
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitIf(this);
        }
    }

    public static final class WhileNode extends ASTNode {
        public final ASTNode condition;
        public final ASTNode body;

        public WhileNode(ASTNode condition, ASTNode body, int start, int end) {
            super(start, end);
            this.condition = condition;
            this.body = body;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitWhile(this);
        }
    }

    public static final class ReturnNode extends ASTNode {
        public final ASTNode value;

        public ReturnNode(ASTNode value, int start, int end) {
            super(start, end);
            this.value = value;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitReturn(this);
        }
    }

    public static final class BinaryNode extends ASTNode {
        public final ASTNode left;
        public final TokenType operator;
        public final ASTNode right;

        public BinaryNode(ASTNode left, TokenType operator, ASTNode right, int start, int end) {
            super(start, end);
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitBinary(this);
        }
    }

    public static final class CallNode extends ASTNode {
        public final String callee;
        public final List<ASTNode> arguments;

        public CallNode(String callee, List<ASTNode> arguments, int start, int end) {
            super(start, end);
            this.callee = callee;
            this.arguments = arguments;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitCall(this);
        }
    }

    public static final class IdentifierNode extends ASTNode {
        public final String name;

        public IdentifierNode(String name, int start, int end) {
            super(start, end);
            this.name = name;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitIdentifier(this);
        }
    }

    public static final class NumberNode extends ASTNode {
        public final double value;

        public NumberNode(double value, int start, int end) {
            super(start, end);
            this.value = value;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitNumber(this);
        }
    }

    public static final class StringNode extends ASTNode {
        public final String value;

        public StringNode(String value, int start, int end) {
            super(start, end);
            this.value = value;
        }

        @Override
        public <R> R accept(ASTVisitor<R> visitor) {
            return visitor.visitString(this);
        }
    }

    public interface ASTVisitor<R> {
        R visitProgram(ProgramNode node);
        R visitFunctionDecl(FunctionDeclNode node);
        R visitVarDecl(VarDeclNode node);
        R visitAssignment(AssignmentNode node);
        R visitBlock(BlockNode node);
        R visitIf(IfNode node);
        R visitWhile(WhileNode node);
        R visitReturn(ReturnNode node);
        R visitBinary(BinaryNode node);
        R visitCall(CallNode node);
        R visitIdentifier(IdentifierNode node);
        R visitNumber(NumberNode node);
        R visitString(StringNode node);
    }

    public static final class Parser {
        private final List<Token> tokens;
        private int current = 0;

        public Parser(List<Token> tokens) {
            this.tokens = tokens != null ? tokens : new ArrayList<>();
        }

        public ProgramNode parse() {
            List<ASTNode> declarations = new ArrayList<>();
            int start = tokens.isEmpty() ? 0 : tokens.get(0).start;
            while (!isAtEnd()) {
                try {
                    declarations.add(parseDeclaration());
                } catch (SyntaxError e) {
                    synchronize();
                }
            }
            int end = tokens.isEmpty() ? 0 : tokens.get(tokens.size() - 1).end;
            return new ProgramNode(declarations, start, end);
        }

        private ASTNode parseDeclaration() {
            if (match(TokenType.TOKEN_FN)) {
                return parseFunctionDecl();
            }
            if (match(TokenType.TOKEN_LET)) {
                return parseVarDecl();
            }
            return parseStatement();
        }

        private FunctionDeclNode parseFunctionDecl() {
            Token nameToken = consume(TokenType.TOKEN_IDENTIFIER, "Expect function name.");
            consume(TokenType.TOKEN_LPAREN, "Expect '(' after function name.");
            List<String> parameters = new ArrayList<>();
            if (!check(TokenType.TOKEN_RPAREN)) {
                do {