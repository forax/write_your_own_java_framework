package org.github.forax.framework.interceptor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InterceptorManagerTest {
  @Nested
  class Q1 {

    @Test
    public void newSimpleProxy() {
      interface Hello {
        String foobar();
        String hello(String message, int value);
      }

      var proxy = InterceptorManager.newSimpleProxy(Hello.class);
      assertAll(
          () -> assertEquals("foobar", proxy.foobar()),
          () -> assertEquals("hello", proxy.hello("a message", 42))
      );
    }
    @Test
    public void newSimpleProxySignature() {
      interface Foo {
        String bar();
      }

      Foo foo = InterceptorManager.newSimpleProxy(Foo.class);
      assertEquals("bar", foo.bar());
    }
    @Test
    public void newSimpleProxyPreconditions() {
      class A {}
      assertAll(
          () -> assertThrows(NullPointerException.class, () -> InterceptorManager.newSimpleProxy(null)),
          () -> assertThrows(IllegalArgumentException.class, () -> InterceptorManager.newSimpleProxy(A.class))
      );
    }

  } // end Q1


  @Nested
  class Q2 {

    @Retention(RUNTIME)
    private @interface Example { }

    @Test
    public void addAndFindInterceptors() throws NoSuchMethodException {
      interface Foo {
        @Example
        void bar();
      }

      Interceptor interceptor = (method, proxy, args, proceed) -> null;
      var bar = Foo.class.getMethod("bar");
      var manager = new InterceptorManager();
      manager.addInterceptor(Example.class, interceptor);
      assertEquals(List.of(interceptor), manager.findInterceptors(bar).toList());
    }
    @Test
    public void addAndFindInterceptorsNoAnnotation() throws NoSuchMethodException {
      interface Foo {
        String bar();
      }

      Interceptor interceptor = (method, proxy, args, proceed) -> null;
      var bar = Foo.class.getMethod("bar");
      var manager = new InterceptorManager();
      manager.addInterceptor(Example.class, interceptor);
      assertEquals(List.of(), manager.findInterceptors(bar).toList());
    }
    @Test
    public void addAndFindInterceptorsNoInterceptor() throws NoSuchMethodException {
      interface Foo {
        @Example
        void bar(int value);
      }

      var bar = Foo.class.getMethod("bar", int.class);
      var manager = new InterceptorManager();
      assertEquals(List.of(), manager.findInterceptors(bar).toList());
    }
    @Test
    public void addInterceptorPreconditions() {
      Interceptor interceptor = (method, proxy, args, proceed) -> null;
      var manager = new InterceptorManager();
      assertAll(
          () -> assertThrows(NullPointerException.class, () -> manager.addInterceptor(null, interceptor)),
          () -> assertThrows(NullPointerException.class, () -> manager.addInterceptor(Example.class, null))
      );
    }

  } // end Q2


  @Nested
  class Q3 {

    @Retention(RUNTIME)
    private @interface Example { }

    @Test
    public void getCallable() throws Exception {
      interface Foo {
        void bar();
      }
      var bar = Foo.class.getMethod("bar");
      var manager = new InterceptorManager();
      Callable<?> callable = manager.getCallable(Stream.empty(), bar, null, new Object[0], null);
      assertNull(callable.call());
    }
    @Test
    public void getCallableProxyHandWritten() throws Exception {
      interface Foo {
        int bar();
      }
      record FooImpl() implements Foo {
        @Override
        public int bar() {
          return 42;
        }
      }
      record FooProxy(Foo delegate) implements Foo {
        @Override
        public int bar() {
          return delegate.bar();
        }
      }
      var bar = Foo.class.getMethod("bar");
      var delegate = new FooImpl();
      var proxy = new FooProxy(delegate);
      var manager = new InterceptorManager();
      var callable = manager.getCallable(Stream.empty(), bar, proxy, new Object[0], delegate);
      assertEquals(42, callable.call());
    }
    @Test
    public void getCallableWithAnInterceptor() throws Exception {
      interface Foo {
        @Example
        int bar();
      }
      record FooImpl() implements Foo {
        @Override
        public int bar() {
          return 101;
        }
      }
      var bar = Foo.class.getMethod("bar");
      var delegate = new FooImpl();
      Interceptor interceptor = (method, proxy, args, proceed) -> 42;
      var manager = new InterceptorManager();
      manager.addInterceptor(Example.class, interceptor);
      var callable = manager.getCallable(Stream.of(interceptor), bar, "proxy", new Object[0], delegate);
      assertEquals(42, callable.call());
    }
    @Test
    public void getCallableWithAnInterceptorGetsTheRightMethod() throws Exception {
      interface Foo {
        @Example
        void bar();
      }
      var bar = Foo.class.getMethod("bar");
      Interceptor interceptor = (method, proxy, args, proceed) -> {
        assertEquals(bar, method);
        return 99;
      };
      var manager = new InterceptorManager();
      manager.addInterceptor(Example.class, interceptor);
      Callable<?> callable = manager.getCallable(Stream.of(interceptor), bar, "proxy", new Object[] { 2 }, null);
      assertEquals(99, callable.call());
    }
    @Test
    public void getCallableWithAnInterceptorThatPropagateCalls() throws Exception {
      interface Foo {
        @Example
        int bar(int multiplier);
      }
      record FooImpl() implements Foo {
        @Override
        public int bar(int multiplier) {
          return 101;
        }
      }
      var bar = Foo.class.getMethod("bar", int.class);
      var delegate = new FooImpl();
      Interceptor interceptor = (method, proxy, args, proceed) -> (Integer) args[0] * (Integer) proceed.call();
      var manager = new InterceptorManager();
      manager.addInterceptor(Example.class, interceptor);
      var callable = manager.getCallable(Stream.of(interceptor), bar, "proxy", new Object[] { 2 }, delegate);
      assertEquals(202, callable.call());
    }
    @Test
    public void getCallableWithTwoInterceptorsThatPropagateCalls() throws Exception {
      interface Foo {
        @Example
        int bar(int adder);
      }
      record FooImpl() implements Foo {
        @Override
        public int bar(int adder) {
          return 51;
        }
      }
      var bar = Foo.class.getMethod("bar", int.class);
      var delegate = new FooImpl();
      Interceptor interceptor = (method, proxy, args, proceed) -> (Integer) args[0] + (Integer) proceed.call();
      var manager = new InterceptorManager();
      manager.addInterceptor(Example.class, interceptor);
      var callable = manager.getCallable(Stream.of(interceptor, interceptor), bar, "proxy", new Object[] { 3 }, delegate);
      assertEquals(57, callable.call());
    }
    @Test
    public void getCallableExceptionPropagation() throws Exception {
      interface Foo {
        int bar();
      }
      record FooImpl() implements Foo {
        @Override
        public int bar() {
          throw new RuntimeException("bar !");
        }
      }
      var bar = Foo.class.getMethod("bar");
      var delegate = new FooImpl();
      var manager = new InterceptorManager();
      var callable = manager.getCallable(Stream.empty(), bar, null, new Object[0], delegate);
      assertThrows(RuntimeException.class, callable::call);
    }

  }  // end Q3


  @Nested
  class Q4 {

    @Retention(RUNTIME)
    @interface BarAnn {}

    @Retention(RUNTIME)
    @interface WhizzAnn {}

    @Test
    public void createProxy() {
      interface Foo {
        @BarAnn
        int bar();

        @WhizzAnn
        String whizz(String s);
      }
      record FooImpl() implements Foo {
        @Override
        public int bar() {
          return 45;
        }

        @Override
        public String whizz(String s) {
          return s.toUpperCase(Locale.ROOT);
        }
      }

      var manager = new InterceptorManager();
      manager.addInterceptor(BarAnn.class, (method, proxy, args, proceed) -> 404);
      manager.addInterceptor(WhizzAnn.class, (method, proxy, args, proceed) -> "*" + proceed.call() + "*");
      Foo foo = manager.createProxy(Foo.class, new FooImpl());
      assertAll(
          () -> assertEquals(404, foo.bar()),
          () -> assertEquals("*HELLO*", foo.whizz("hello"))
      );
    }
    @Test
    public void createProxyNoDelegate() {
      interface Foo {
        @BarAnn
        int bar();
      }

      var manager = new InterceptorManager();
      manager.addInterceptor(BarAnn.class, (method, proxy, args, proceed) -> proceed.call());
      Foo foo = manager.createProxy(Foo.class, null);
      assertEquals(0, foo.bar());
    }

  }  // end Q4


  @Nested
  class Q5 {

    @Retention(RUNTIME)
    @interface BarAnn {}

    @Test
    public void severalInterceptorOnTheSameAnnotation() {
      interface Foo {
        @BarAnn
        String bar(String s);
      }
      record FooImpl() implements Foo {
        @Override
        public String bar(String s) {
          return s;
        }
      }

      var manager = new InterceptorManager();
      manager.addInterceptor(BarAnn.class, (method, proxy, args, proceed) -> "_" + proceed.call() + "_");
      manager.addInterceptor(BarAnn.class, (method, proxy, args, proceed) -> "*" + proceed.call() + "*");
      Foo foo = manager.createProxy(Foo.class, new FooImpl());
      assertEquals("_*hello*_", foo.bar("hello"));
    }

  }  // end Q5

  @Nested
  class Q6 {

    @Retention(RUNTIME)
    private @interface Example { }

    @Test
    public void getFun() throws Exception {
      interface Foo {
        void bar();
      }
      var bar = Foo.class.getMethod("bar");
      var manager = new InterceptorManager();
      var fun = manager.getFun(Stream.empty(), bar);
      assertNull(fun.apply(null, new Object[0], null));
    }
    @Test
    public void getFunProxyHandWritten() throws Exception {
      interface Foo {
        int bar();
      }
      record FooImpl() implements Foo {
        @Override
        public int bar() {
          return 42;
        }
      }
      record FooProxy(Foo delegate) implements Foo {
        @Override
        public int bar() {
          return delegate.bar();
        }
      }
      var bar = Foo.class.getMethod("bar");
      var delegate = new FooImpl();
      var proxy = new FooProxy(delegate);
      var manager = new InterceptorManager();
      var fun = manager.getFun(Stream.empty(), bar);
      assertEquals(42, fun.apply(proxy, new Object[0], delegate));
    }
    @Test
    public void getFunWithAnInterceptor() throws Exception {
      interface Foo {
        @Example
        int bar();
      }
      record FooImpl() implements Foo {
        @Override
        public int bar() {
          return 101;
        }
      }
      var bar = Foo.class.getMethod("bar");
      var delegate = new FooImpl();
      Interceptor interceptor = (method, proxy, args, proceed) -> 42;
      var manager = new InterceptorManager();
      manager.addInterceptor(Example.class, interceptor);
      var fun = manager.getFun(Stream.of(interceptor), bar);
      assertEquals(42, fun.apply("proxy", new Object[0], delegate));
    }
    @Test
    public void getFunWithAnInterceptorGetsTheRightMethod() throws Exception {
      interface Foo {
        @Example
        void bar();
      }
      var bar = Foo.class.getMethod("bar");
      Interceptor interceptor = (method, proxy, args, proceed) -> {
        assertEquals(bar, method);
        return 99;
      };
      var manager = new InterceptorManager();
      manager.addInterceptor(Example.class, interceptor);
      var fun = manager.getFun(Stream.of(interceptor), bar);
      assertEquals(99, fun.apply("proxy", new Object[] { 2 }, null));
    }
    @Test
    public void getFunWithAnInterceptorThatPropagateCalls() throws Exception {
      interface Foo {
        @Example
        int bar(int multiplier);
      }
      record FooImpl() implements Foo {
        @Override
        public int bar(int multiplier) {
          return 101;
        }
      }
      var bar = Foo.class.getMethod("bar", int.class);
      var delegate = new FooImpl();
      Interceptor interceptor = (method, proxy, args, proceed) -> (Integer) args[0] * (Integer) proceed.call();
      var manager = new InterceptorManager();
      manager.addInterceptor(Example.class, interceptor);
      var fun = manager.getFun(Stream.of(interceptor), bar);
      assertEquals(202, fun.apply("proxy", new Object[] { 2 }, delegate));
    }
    @Test
    public void getFunWithTwoInterceptorsThatPropagateCalls() throws Exception {
      interface Foo {
        @Example
        int bar(int adder);
      }
      record FooImpl() implements Foo {
        @Override
        public int bar(int adder) {
          return 51;
        }
      }
      var bar = Foo.class.getMethod("bar", int.class);
      var delegate = new FooImpl();
      Interceptor interceptor = (method, proxy, args, proceed) -> (Integer) args[0] + (Integer) proceed.call();
      var manager = new InterceptorManager();
      manager.addInterceptor(Example.class, interceptor);
      var fun = manager.getFun(Stream.of(interceptor, interceptor), bar);
      assertEquals(57, fun.apply( "proxy", new Object[] { 3 }, delegate));
    }
    @Test
    public void getFunExceptionPropagation() throws Exception {
      interface Foo {
        int bar();
      }
      record FooImpl() implements Foo {
        @Override
        public int bar() {
          throw new RuntimeException("bar !");
        }
      }
      var bar = Foo.class.getMethod("bar");
      var delegate = new FooImpl();
      var manager = new InterceptorManager();
      var fun = manager.getFun(Stream.empty(), bar);
      assertThrows(RuntimeException.class,  () -> fun.apply(null, new Object[0], delegate));
    }

  }  // end Q6

  @Nested
  class Q7 {

    @Retention(RUNTIME)
    @interface Example1 {}

    @Retention(RUNTIME)
    @interface Example2 {}

    @Test
    public void proxiesSharingTheSmeInterface() {
      interface Foo {
        @Example1 @Example2
        String hello(String message);
      }
      var manager = new InterceptorManager();
      manager.addInterceptor(Example1.class, (method, proxy, args, proceed) -> proceed.call() + " !!");
      manager.addInterceptor(Example2.class, (method, proxy, args, proceed) -> proceed.call() + " $$");
      var foo = manager.createProxy(Foo.class, message -> message);
      assertEquals("helllo $$ !!", foo.hello("helllo"));
    }
  }

  @Nested
  class Q8 {

    @Retention(RUNTIME)
    @interface Example1 {}

    @Retention(RUNTIME)
    @interface Example2 {}

    @Retention(RUNTIME)
    @interface Example3 {}

    @Test
    public void annotationOnClassMethodAndParameters() {
      @Example1
      interface Foo {
        @Example2
        default String hello(@Example3 String message) {
          return message;
        }
      }
      var manager = new InterceptorManager();
      manager.addInterceptor(Example1.class, (method, proxy, args, proceed) -> "1" + proceed.call());
      manager.addInterceptor(Example2.class, (method, proxy, args, proceed) -> "2" + proceed.call());
      manager.addInterceptor(Example3.class, (method, proxy, args, proceed) -> "3" + proceed.call());
      var foo = manager.createProxy(Foo.class, new Foo() {});
      assertEquals("123", foo.hello(""));
    }
  }
}