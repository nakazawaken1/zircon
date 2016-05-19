package zircon;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

@RunWith(Tester.Runner.class)
public class Tester {

    Deque<Tester> stack;

    Description description;

    Supplier<Object> getter;

    Runnable before;

    Runnable test;

    Runnable after;

    List<Tester> children;

    public Tester() {
        stack = new LinkedList<>();
        stack.push(this);
        description = Description.createSuiteDescription(getClass().getName());
    }

    Tester(Deque<Tester> stack, String name, Supplier<Object> supplier, Runnable action) {
        this.stack = stack;
        description = Description.createSuiteDescription(name);
        getter = () -> {
            if (action != null) {
                action.run();
            }
            return supplier != null ? supplier.get() : null;
        };
    }

    public Expect expect(String name, Supplier<Object> supplier) {
        return new Expect(stack.peek().add(new Tester(stack, name, supplier, null)));
    }

    public Expect expect(String name, Runnable action) {
        return new Expect(stack.peek().add(new Tester(stack, name, null, action)));
    }

    public void group(String name, Runnable action) {
        stack.push(stack.peek().add(new Tester(stack, name, null, null)));
        action.run();
        stack.pop();
    }

    public void beforeEach(Runnable action) {
        stack.peek().before = action;
    }

    public void afterEach(Runnable action) {
        stack.peek().after = action;
    }

    Tester add(Tester child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
        description.addChild(child.description);
        return child;
    }

    Object get() {
        return getter == null ? null : getter.get();
    }

    void run(RunNotifier notifier) {
        notifier.fireTestStarted(description);
        try {
            if (test != null) {
                test.run();
            }
            if (children != null) {
                for (Tester child : children) {
                    if (before != null) {
                        before.run();
                    }
                    child.run(notifier);
                    if (after != null) {
                        after.run();
                    }
                }
            }
        } catch (Exception e) {
            notifier.fireTestFailure(new Failure(description, e));
        } finally {
            notifier.fireTestFinished(description);
        }
    }

    public static class Expect {

        Tester tester;

        Expect(Tester tester) {
            this.tester = tester;
        }

        Object get(Object... expected) {
            return expected.length == 1 ? expected[0]
                    : Stream.of(expected).map(Objects::toString).collect(Collectors.joining(System.lineSeparator()));
        }

        public void toEqual(Object... expected) {
            tester.test = () -> Assert.assertEquals(get(expected), tester.get());
        }

        public void toNotEqual(Object... expected) {
            tester.test = () -> Assert.assertNotEquals(get(expected), tester.get());
        }

        public void toThrow(Class<? extends Throwable> expected) {
            tester.test = () -> {
                try {
                    tester.get();
                } catch (Throwable e) {
                    if (!expected.isAssignableFrom(e.getClass())) {
                        Assert.fail("expected to throw <" + expected.getName() + ">, bat was <" + e.getClass().getName() + ">");
                    }
                }
            };
        }

        public void toOutput(String... expected) {
            tester.test = () -> {
                PrintStream backup = System.out;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (PrintStream out = new PrintStream(buffer)) {
                    System.setOut(out);
                    tester.get();
                    Assert.assertEquals(String.join(System.lineSeparator(), expected), buffer.toString().trim());
                }
                System.setOut(backup);
            };
        }

        public void toErrorOutput(String... expected) {
            tester.test = () -> {
                PrintStream backup = System.err;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (PrintStream out = new PrintStream(buffer)) {
                    System.setErr(out);
                    tester.get();
                    Assert.assertEquals(String.join(System.lineSeparator(), expected), buffer.toString().trim());
                }
                System.setErr(backup);
            };
        }
    }

    public static class Runner extends org.junit.runner.Runner {

        final Tester tester;

        public Runner(Class<? extends Tester> testClass) throws InstantiationException, IllegalAccessException {
            tester = testClass.newInstance();
        }

        @Override
        public Description getDescription() {
            return tester.description;
        }

        @Override
        public void run(RunNotifier notifier) {
            tester.run(notifier);
        }
    }
}
