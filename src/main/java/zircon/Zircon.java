package zircon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import zircon.util.LogFormatter;

/**
 * プログラミング言語 Zircon
 */
public class Zircon {

    public static void main(String[] args) {
        // ログ出力設定
        LogFormatter.setup();
        String source = String.join(" ", args);
        Zircon.run(source.isEmpty() ? "$.echo" : source);
    }

    /**
     * 実行
     * @param source ソース
     */
    public static void run(String source) {
        run(source, null, System.out);
    }

    /**
     * 実行して標準出力を取得
     * @param source ソース
     */
    public static String get(String source) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); PrintStream stream = new PrintStream(out)) {
            run(source, null, stream);
            return out.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 実行
     * @param source ソース
     * @param config 初期設定
     * @param out 標準出力
     */
    public static void run(String source, Consumer<Dictionary> config, PrintStream out) {
        Logger.getLogger(Zircon.class.getName()).info(source);
        World world = new World(null);
        try {
            world.put("$", world).put("echo", new ZrNative(PrintStream.class.getMethod("println", Object.class), out))
                    .put("print", new ZrNative(PrintStream.class.getMethod("print", Object.class), out))
                    .put("env", new Can(m -> Optional.ofNullable(System.getenv())
                            .ifPresent(env -> env.entrySet().stream().forEach(i -> m.put(i.getKey(), i.getValue())))));
        } catch (NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        if (config != null)
            config.accept(world);
        for (Ast ast : new Parser(source)) {
            if (ast != null) {
                Logger.getLogger(Zircon.class.getName()).info("" + ast);
                System.err.println("#> " + ast.calc(world));
            }
        }
    }

    /**
     * @param setter
     * @return
     */
    @SafeVarargs
    public static Can can(Consumer<Can>... setter) {
        return new Can(setter);
    }

    /**
     * 改行文字
     */
    static final String lineSeparator = System.lineSeparator();

    /**
     * 実行時エラー
     * @param e 例外
     * @return 実行時例外
     */
    static RuntimeException error(Exception e) {
        return new RuntimeException(e);
    }

    /**
     * @author user
     */
    static class Parser implements Iterable<Ast> {

        /**
         * 空白
         */
        static final int[] spaces = { ' ', '\t' };

        /**
         * 改行
         */
        static final int[] newlines = { '\r', '\n' };

        /**
         * 数字
         */
        static final int[] digits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };

        /**
         * 記号('!', '$', '?'以外)
         */
        static final int[] marks = { '"', '#', '%', '&', '\'', '(', ')', '=', '-', '~', '^', '|', '\\', '`', '@', '{', '[', '+', '*', ':',
                '}', ']', '<', ',', '>', '.', '/' };

        /**
         * 接頭辞
         */
        static final String[] prefixes = { "+", "-", "not", "exists", "empty" };

        /**
         * 演算子辞書(キー：演算子, 値:優先順位（正は左結合、負は右結合））
         */
        @SuppressWarnings("serial")
        static final Map<String, Integer> operatorMap = new LinkedHashMap<String, Integer>() {
            {
                put(":", -1);
                put("and", 2);
                put("or", 2);
                put("<=", 3);
                put("<>", 3);
                put("<", 3);
                put("=", 3);
                put(">=", 3);
                put(">", 3);
                put("is", 4);
                put("in", 4);
                put("&", 5);
                put("+", 5);
                put("-", 5);
                put("*", 6);
                put("/", 6);
                put("%", 6);
                put("\\", 6);
                put("^", 7);
            }
        };

        /**
         * 演算子
         */
        static final String[] operators = operatorMap.keySet().toArray(new String[] {});

        /**
         * ソース
         */
        String source;

        /**
         * 最終位置
         */
        int lastIndex;

        /**
         * 現在位置
         */
        int index;

        /* (non-Javadoc)
         * @see java.lang.Iterable#iterator() */
        @Override
        public Iterator<Ast> iterator() {
            return new Iterator<Ast>() {

                /* (non-Javadoc)
                 * @see java.util.Iterator#hasNext() */
                @Override
                public boolean hasNext() {
                    return index < lastIndex;
                }

                /* (non-Javadoc)
                 * @see java.util.Iterator#next() */
                @Override
                public Ast next() {
                    return program();
                }

            };
        }

        /**
         * コンストラクタ
         * @param source ソース
         */
        Parser(String source) {
            this.source = source;
            this.lastIndex = source == null ? 0 : source.length();
            this.index = 0;
        }

        /**
         * 許可文字かどうか
         * @param allows 許可文字（省略時は全文字を許可文字とする）
         * @return 現在位置の文字（'\0'は許可文字以外または終端）
         */
        int peek(int... allows) {
            if (index < lastIndex) {
                int c = source.charAt(index);
                if (allows == null || allows.length <= 0)
                    return c;
                for (int i : allows)
                    if (i == c)
                        return c;
            }
            return '\0';
        }

        /**
         * 許可文字をスキップする
         * @param allows 許可文字（省略時は空白文字）
         * @return スキップした文字数
         */
        int skip(int... allows) {
            if (allows == null || allows.length <= 0)
                allows = spaces;
            int start = index;
            while (peek(allows) != '\0')
                index++;
            return index - start;
        }

        /**
         * 許可文字をスキップする
         * @param allows 許可文字（省略時は空白文字）
         * @return スキップした文字数
         */
        int skip(int[] a1, int[]... a2) {
            int start = index;
            List<int[]> list = new ArrayList<>();
            list.add(a1);
            for (int[] a : a2)
                list.add(a);
            while (!list.stream().map(this::peek).allMatch(i -> i == '\0'))
                index++;
            return index - start;
        }

        /**
         * 許可文字列をスキップ
         * @param allows 許可文字列
         * @return スキップした許可文字列（emptyの場合は許可文字列に一致しなかった）
         */
        Optional<String> eat(String... allows) {
            skip();
            outer: for (String i : allows == null ? new String[] {} : allows) {
                int offset = 0;
                for (int j : i.toCharArray()) {
                    if (peek(j) == '\0')
                        continue outer;
                    offset++;
                }
                // シンボルの切れ目があるかどうかチェック
                if (peek(marks) != 0 || peek(spaces) != 0 || peek(newlines) != 0) {
                    skip();
                    index += offset;
                    return Optional.of(i);
                }
            }
            return Optional.empty();
        }

        /**
         * 抽象構文木が取得できるか試す
         * @param ast 抽象構文木取得メソッド
         * @return 抽象構文木（エラー時はnull）
         */
        Optional<Ast> test(Supplier<Ast> ast) {
            int start = index;
            try {
                return Optional.of(ast.get());
            } catch (RuntimeException e) {
                Logger.getLogger(getClass().getName()).config("rollback " + index + " -> " + start);
                index = start;
                return Optional.empty();
            }
        }

        /**
         * @return statement ( newlines | ';' | END_OF_SOURCE )
         */
        Ast program() {
            Logger.getLogger(getClass().getName()).config("" + index);
            final int[] separators = { ';' };
            Ast r = statement();
            if (peek(newlines) == '\0' && peek(separators) == '\0' && peek() != '\0') {
                throw error("excepted newline");
            }
            skip(newlines, separators);
            return r;
        }

        /**
         * if : 'if' expression block { 'ef' expression block } [ 'else' block ] for: 'for' ( block | expression block | symbol 'in'
         * expression block ) do : 'do' [ symbol ] { ',' symbol } [ ',' ] ( ':' expression | block )
         * @return [ 'return' ] ( if | for | do | simple )
         */
        Ast statement() {
            Logger.getLogger(getClass().getName()).config("" + index);
            Optional<String> Return = eat("return");
            Ast ast;
            if (eat("if").isPresent()) {
                List<Ast> list = new ArrayList<>();
                list.add(expression());
                list.add(block());
                while (eat("ef").isPresent()) {
                    list.add(expression());
                    list.add(block());
                }
                if (eat("else").isPresent()) {
                    list.add(block());
                }
                ast = new ZrIf(list.toArray(new Ast[] {}));
                /* } else if (eat("for").isPresent()) { } else if (eat("do").isPresent()) { */

            } else {
                ast = simple();
            }
            return Return.<Ast> map(x -> new ZrReturn(ast)).orElse(ast);
        }

        /**
         * @return expression [ tuple ]
         */
        Ast simple() {
            Logger.getLogger(getClass().getName()).config("" + index);
            Ast e = expression();
            return test(this::tuple).<Ast> map(t -> new ZrPrimitive(e, t.children.toArray(new Ast[] {}))).orElse(e);
        }

        /**
         * @return prefix { operator prefix }
         */
        Ast expression() {
            Logger.getLogger(getClass().getName()).config("" + index);
            Ast right = prefix();
            Optional<Map.Entry<String, Integer>> next;
            while ((next = operator()).isPresent()) {
                right = shift(right, next.get());
            }
            return right;
        }

        /**
         * @return [ '+'|'-'|'not'|'exists'|'empty' ] primitive
         */
        Ast prefix() {
            Logger.getLogger(getClass().getName()).config("" + index);
            return eat(prefixes).<Ast> map(p -> new ZrPrefix(new ZrSymbol(p), primitive())).orElseGet(() -> primitive());
        }

        /**
         * @return 次の演算子
         */
        Optional<Map.Entry<String, Integer>> operator() {
            return eat(operators).map(o -> new AbstractMap.SimpleEntry<String, Integer>(o, operatorMap.get(o)));
        }

        /**
         * @param left 左辺
         * @param operatorEntry 演算子情報
         * @return 二項演算
         */
        Ast shift(Ast left, Map.Entry<String, Integer> operatorEntry) {
            Ast right = prefix();
            Optional<Map.Entry<String, Integer>> next;
            int value = operatorEntry.getValue();
            while ((next = operator()).isPresent() && (value < 0 ? value <= next.get().getValue() : value < next.get().getValue())) {
                right = shift(right, next.get());
            }
            return new ZrBinaryOperator(left, new ZrSymbol(operatorEntry.getKey()), right);
        }

        /**
         * @return ( 'true' | 'false' | argument | string | number | symbol ) { postfix }
         */
        Ast primitive() {
            Logger.getLogger(getClass().getName()).config("" + index);
            Ast ast = eat("true", "false").<Ast> map(s -> "true".equals(s) ? ZrBoolean.TRUE : ZrBoolean.FALSE)
                    .orElseGet(() -> test(this::string).orElseGet(() -> test(this::number).orElseGet(() -> test(this::symbol)
                            .orElseGet(() -> test(this::argument).orElseThrow(() -> error("excepted primitive"))))));
            for (;;) {
                Optional<Ast> p = test(this::postfix);
                if (p.isPresent())
                    ast = new ZrPrimitive(ast, p.get());
                else
                    break;
            }
            return ast;
        }

        /**
         * @return '.' symbol | argument
         */
        Ast postfix() {
            Logger.getLogger(getClass().getName()).config("" + index);
            return eat(".").map(x -> symbol()).orElseGet(() -> argument());
        }

        /**
         * @return '(' ( { newlines } | expression { ( ',' | newlines ) expression } [ ',' ] ) ')'
         */
        Ast argument() {
            Logger.getLogger(getClass().getName()).config("" + index);
            eat("(").orElseThrow(() -> error("expected ("));
            Ast ast = test(this::expression).map(e -> {
                List<Ast> values = new ArrayList<>();
                values.add(e);
                while (eat(",").isPresent()) {
                    skip(spaces, newlines);
                    values.add(expression());
                }
                return values.size() == 1 ? e : new Ast(values);
            }).orElseGet(() -> {
                skip(spaces, newlines);
                return new Ast();
            });
            eat(")").orElseThrow(() -> error("expected )"));
            return ast;
        }

        /**
         * @return expression { ',' expression }
         */
        Ast tuple() {
            Logger.getLogger(getClass().getName()).config("" + index);
            List<Ast> values = new ArrayList<>();
            values.add(expression());
            while (eat(",").isPresent()) {
                skip();
                values.add(expression());
            }
            return new Ast(values);
        }

        /**
         * @return '{' ( { newlines } | statement { newlines statement } [ newlines ] ) '}'
         */
        Ast block() {
            Logger.getLogger(getClass().getName()).config("" + index);
            eat("{").orElseThrow(() -> error("expected {"));
            Ast ast = test(this::statement).map(e -> {
                List<Ast> values = new ArrayList<>();
                values.add(e);
                while (eat(",").isPresent()) {
                    skip(spaces, newlines);
                    values.add(statement());
                }
                return new Ast(values);
            }).orElseGet(() -> {
                skip(spaces, newlines);
                return new Ast();
            });
            eat("}").orElseThrow(() -> error("expected }"));
            return ast;
        }

        /**
         * @return "'" _ { ^"''"^ | "'" } _ "'" | '"' _ { ^'"'^ | '\"' } _ '"'
         */
        Ast string() {
            Logger.getLogger(getClass().getName()).config("" + index);
            skip();
            int start = index;
            switch (peek()) {
            case '\'':
                index++;
                while (index < lastIndex) {
                    if (source.charAt(index++) == '\'') {
                        if (peek() != '\'') {
                            return new ZrString(source.substring(start + 1, index - 1).replace("''", "'"));
                        }
                        index++;
                    }
                }
                throw error("expected '");
            case '"':
                index++;
                int old = '\0';
                while (index < lastIndex) {
                    int now = source.charAt(index++);
                    if (now == '\\' && old == '\\') {
                        old = '\0';
                    }
                    if (now == '"' && old != '\\') {
                        return new ZrString(source.substring(start + 1, index - 1));
                    }
                    old = now;
                }
                throw error("expected \"");
            }
            throw error("expected string");
        }

        /**
         * @return digits _ { '_' | digits } _ [ '.' _ digits _ { '_' | digits } ]
         */
        Ast number() {
            Logger.getLogger(getClass().getName()).config("" + index);
            final int[] separators = { '_' };
            skip();
            if (peek(digits) == '\0')
                throw error("expected digit");
            int start = index;
            skip(separators, digits);
            if (skip('.') == 1) {
                if (skip(separators, digits) <= 0) { // .で終わる場合はメソッド呼び出し
                    index--;
                }
            }
            return new ZrNumber(source.substring(start, index));
        }

        /**
         * @return ^digits|marks|spaces|newlines|';'^ _ { ^marks|spaces|newlines|';'^ }
         */
        Ast symbol() {
            Logger.getLogger(getClass().getName()).config("" + index);
            skip();
            if (peek(digits) != '\0' || peek(marks) != '\0' || peek(spaces) != '\0' || peek(newlines) != '\0' || peek(';') != '\0') {
                throw error("expected symbol");
            }
            int start = index;
            index++;
            while (index < lastIndex && peek(marks) == '\0' && peek(spaces) == '\0' && peek(newlines) == '\0' && peek(';') == '\0') {
                index++;
            }
            return new ZrSymbol(source.substring(start, index));
        }

        /**
         * 文法エラー
         * @param message エラー内容
         * @return 文法エラー例外
         */
        RuntimeException error(String message) {
            int end = Arrays.stream(newlines).map(i -> source.indexOf(i, index)).filter(i -> i >= 0).min().orElse(lastIndex);
            return new RuntimeException(message + " (" + index + "文字目) " + source.substring(index, end));
        }
    }

    /**
     * 辞書インターフェース
     */
    public interface Dictionary extends Iterable<Map.Entry<String, Object>> {
        /**
         * キーに対応する値を取得
         * @param key キー
         * @return 値
         */
        Optional<Object> get(String key);

        /**
         * キーと値を格納
         * @param key キー
         * @param value 値
         * @return 自分自身
         */
        Dictionary put(String key, Object value);

        /**
         * キーが存在するか
         * @param key キー
         * @return true:存在する, false:しない
         */
        boolean has(String key);

        /**
         * @return 文字列
         */
        default String string() {
            StringBuffer s = new StringBuffer("[").append(lineSeparator);
            for (Map.Entry<String, Object> i : this) {
                Object value = i.getValue();
                s.append(i.getKey()).append('=').append(value == this ? "(self)" : value).append(lineSeparator);
            }
            return s.append(']').append(lineSeparator).toString();
        }
    }

    /**
     * 辞書実装
     */
    public static class Can implements Dictionary {

        @SafeVarargs
        Can(Consumer<Can>... setter) {
            Arrays.stream(setter).forEach(i -> i.accept(this));
        }

        @Override
        public String toString() {
            return string();
        }

        /**
         * 格納先
         */
        Map<String, Object> map = new HashMap<>();

        /* (non-Javadoc)
         * @see zircon.Zircon.Dictionary#put(java.lang.String, java.lang.Object) */
        @Override
        public Dictionary put(String key, Object value) {
            map.put(key, value);
            return this;
        }

        /* (non-Javadoc)
         * @see zircon.Zircon.Dictionary#get(java.lang.String) */
        @Override
        public Optional<Object> get(String key) {
            return Optional.ofNullable(map.get(key));
        }

        /* (non-Javadoc)
         * @see zircon.Zircon.Dictionary#has(java.lang.String) */
        @Override
        public boolean has(String key) {
            return map.containsKey(key);
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return map.entrySet().iterator();
        }
    }

    /**
     * 階層構造の辞書
     */
    static class World extends Can {
        /**
         * 外側の辞書
         */
        Optional<World> outer;

        /**
         * コンストラクタ
         * @param outer 外側の辞書(null可)
         */
        World(World outer) {
            this.outer = Optional.ofNullable(outer);
        }

        /**
         * 自身にキーがない場合は順に外側を検索し、存在する値を更新、 存在しな場合は自身に格納
         * @see zircon.Zircon.Can#put(java.lang.String, java.lang.Object)
         */
        @Override
        public Can put(String key, Object value) {
            if (super.has(key)) {
                super.put(key, value);
            } else if (outer.isPresent() && outer.get().has(key)) {
                outer.get().put(key, value);
            } else {
                super.put(key, value);
            }
            return this;
        }

        /**
         * 自身にキーがない場合は順に外側を検索
         * @see zircon.Zircon.Can#get(java.lang.String)
         */
        @Override
        public Optional<Object> get(String key) {
            return Optional.ofNullable((super.get(key).orElseGet(() -> outer.map(o -> o.get(key)))));
        }
    }

    /**
     * 構文木
     */
    static class Ast {
        /* (non-Javadoc)
         * @see java.lang.Object#toString() */
        @Override
        public String toString() {
            return getClass().getSimpleName() + Objects.toString(children);
        }

        /**
         * 子リスト
         */
        List<Ast> children;

        /**
         * コンストラクタ
         * @param children 子リスト
         */
        Ast(Ast... children) {
            this.children = Arrays.asList(children);
            String s = toString();
            if (s != null)
                Logger.getLogger(getClass().getName()).config(s);
        }

        /**
         * コンストラクタ
         * @param children 子リスト
         */
        Ast(List<Ast> children) {
            this.children = children;
            String s = toString();
            if (s != null) {
                Logger.getLogger(getClass().getName()).config(s);
            }
        }

        /**
         * 評価
         * @param world 環境
         * @return 評価結果
         */
        Object calc(World world) {
            return children.stream().map(i -> i.calc(world)).toArray();
        }
    }

    /**
     * 値を一つ持つ抽象構文木
     * @param <T> 値の型
     */
    static class One<T> extends Ast {
        @Override
        Object calc(World world) {
            return value;
        }

        /**
         * @return 文字列
         */
        @Override
        public String toString() {
            return value == null ? null : getClass().getSimpleName() + '[' + value + ']';
        }

        /**
         * 値
         */
        T value;

        /**
         * コンストラクタ
         * @param value 値
         */
        One(T value) {
            super(Arrays.asList());
            this.value = value;
            String s = toString();
            if (s != null) {
                Logger.getLogger(getClass().getName()).config(s);
            }
        }
    }

    /**
     * if
     */
    static class ZrIf extends Ast {
        @Override
        Object calc(World world) {
            Iterator<Ast> i = children.iterator();
            Object c = null;
            for (;;) {
                c = i.next().calc(world);
                if (!i.hasNext())
                    break;
                if ((boolean) c)
                    c = i.next().calc(world);
            }
            return c;
        }

        ZrIf(Ast... condition_actions_else) {
            super(condition_actions_else);
        }
    }

    /**
     * return
     */
    static class ZrReturn extends Ast {
        @Override
        Object calc(World world) {
            return children.get(0).calc(world);
        }

        ZrReturn(Ast expression) {
            super(expression);
        }
    }

    /**
     * if
     */
    static class ZrPrefix extends Ast {

        @Override
        Object calc(World world) {
            Object v = children.get(1).calc(world);
            switch (((ZrSymbol) children.get(0)).value) {
            case "+":
            break;
            case "-":
                v = ((BigDecimal) v).negate();
            break;
            case "not":
                v = !(boolean) v;
            break;
            case "exists":
                v = v != null;
            break;
            case "empty":
                v = v == null;
            break;
            }
            return v;
        }

        ZrPrefix(Ast... prefix_primitive) {
            super(prefix_primitive);
        }
    }

    /**
     * 論理値
     */
    static class ZrBoolean extends One<Boolean> {

        ZrBoolean(String value) {
            super("true".equalsIgnoreCase(value));
        }

        static final ZrBoolean TRUE = new ZrBoolean("true");
        static final ZrBoolean FALSE = new ZrBoolean("false");
    }

    /**
     * 数値
     */
    static class ZrNumber extends One<BigDecimal> {

        ZrNumber(String value) {
            super(new BigDecimal(value));
        }
    }

    /**
     * 文字列
     */
    static class ZrString extends One<String> {

        /**
         * @param value
         */
        ZrString(String value) {
            super(value);
        }
    }

    /**
     * 加算
     */
    static class ZrBinaryOperator extends Ast {

        @Override
        Object calc(World world) {
            BigDecimal left = (BigDecimal) children.get(0).calc(world);
            String operator = ((ZrSymbol) children.get(1)).value;
            BigDecimal right = (BigDecimal) children.get(2).calc(world);
            switch (operator) {
            case "+":
                return left.add(right);
            case "-":
                return left.subtract(right);
            case "*":
                return left.multiply(right);
            case "/":
                return left.divide(right);
            case "%":
                return left.divideAndRemainder(right)[1];
            case "\\":
                return left.divideAndRemainder(right)[0];
            case "^":
                return left.pow(right.intValue());
            }
            throw new RuntimeException("Unsupported operator : " + operator);
        }

        ZrBinaryOperator(Ast... left_operator_right) {
            super(left_operator_right);
        }
    }

    /**
     * 単項
     */
    static class ZrPrimitive extends One<Ast> {

        @Override
        public String toString() {
            return children == null || value == null ? null : getClass().getName() + '[' + value + ", " + Objects.toString(children) + ']';
        }

        ZrPrimitive(Ast value, Ast... parameters) {
            super(null);
            this.value = value;
            this.children = Arrays.asList(parameters);
            Logger.getLogger(getClass().getName()).config(toString());
        }

        @Override
        Object calc(World world) {
            Object value = this.value.calc(world);
            if (children.isEmpty())
                return value;
            if (value instanceof ZrNative) {
                ZrNative n = (ZrNative) value;
                Object[] args = children.stream().map(i -> i.calc(world)).collect(Collectors.toList()).toArray();
                try {
                    return n.method.invoke(n.object, args);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    error(e);
                }
            }
            if (value instanceof ZrFunction) {
                ZrFunction f = (ZrFunction) value;
                World newWorld = f.newWorld();
                int i = 0;
                for (Ast a : children) {
                    newWorld.put(f.name(i++), a.calc(world));
                }
                return f.body.calc(newWorld);
            }
            Object value2 = children.get(0).calc(world);
            if (value2 instanceof ZrNative) {
                ZrNative n = (ZrNative) value2;
                try {
                    return n.method.invoke(n.object, value);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    error(e);
                }
            }
            if (value2 instanceof ZrFunction) {
                ZrFunction f = (ZrFunction) value2;
                World newWorld = f.newWorld();
                newWorld.put(f.name(0), value);
                return f.body.calc(newWorld);
            }
            throw error(null);
        }
    }

    /**
     * 関数
     */
    static class ZrFunction extends Ast {
        List<String> parameters;
        Ast body;
        World world;

        ZrFunction(World world, Ast body, String... parameters) {
            this.world = world;
            this.body = body;
            this.parameters = Arrays.asList(parameters);
        }

        World newWorld() {
            return new World(world);
        }

        String name(int i) {
            return parameters.get(i);
        }

        @Override
        Object calc(World world) {
            return this;
        }
    }

    /**
     * システムコール
     */
    static class ZrNative extends Ast {
        @Override
        public String toString() {
            return method == null ? null : getClass().getName() + '[' + method.getName() + ']';
        }

        Method method;
        Object object;

        ZrNative(Method method, Object object) {
            this.method = method;
            this.object = object;
            Logger.getLogger(getClass().getName()).config(toString());
        }

        @Override
        Object calc(World world) {
            return this;
        }
    }

    /**
     * シンボル
     */
    static class ZrSymbol extends One<String> {
        ZrSymbol(String value) {
            super(value);
        }

        @Override
        Object calc(World world) {
            return world.get(value).orElse(this);
        }
    }
}
