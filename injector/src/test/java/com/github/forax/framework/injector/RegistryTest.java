package com.github.forax.framework.injector;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("unused")
public class RegistryTest {
  @Nested
  class Q1 {
    @Test @Tag("Q1")
    public void getRegistry() {
      var registry = new Registry();
      assertNotNull(registry);
    }

    @Test @Tag("Q1")
    public void atInjectTargetMethodAndConstructorAndRetentionIsRuntime() {
      assertEquals(List.of(METHOD, CONSTRUCTOR), List.of(Inject.class.getAnnotation(Target.class).value()));
    }
  }


  @Nested
  class Q2 {

    @Test @Tag("Q2")
    public void registerInstanceAndGetInstanceString() {
      var registry = new Registry();
      registry.registerInstance(String.class, "hello");
      assertEquals("hello", registry.getInstance(String.class));
    }

    @Test @Tag("Q2")
    public void registerInstanceAndGetInstanceInteger() {
      var registry = new Registry();
      registry.registerInstance(Integer.class, 42);
      assertEquals(42, registry.getInstance(Integer.class));
    }

    @Test @Tag("Q2")
    public void registerInstanceAndGetInstanceSameInstance() {
      record Person(String name) {}

      var registry = new Registry();
      var bob = new Person("Bob");
      registry.registerInstance(Person.class, bob);
      assertSame(bob, registry.getInstance(Person.class));
    }

    @Test @Tag("Q2")
    public void registerInstanceAndGetInstanceWithAnInterface() {
      interface I {
        String hello();
      }
      class Impl implements I {
        @Override
        public String hello() {
          return "hello";
        }
      }

      var registry = new Registry();
      var impl = new Impl();
      registry.registerInstance(I.class, impl);
      assertSame(impl, registry.getInstance(I.class));
    }

    @Test @Tag("Q2")
    public void registerInstancePreconditions() {
      var registry = new Registry();
      assertAll(
          () -> assertThrows(NullPointerException.class, () -> registry.registerInstance(null, new Object())),
          () -> assertThrows(NullPointerException.class, () -> registry.registerInstance(Consumer.class, null))
      );
    }
  }


  @Nested
  class Q3 {
    @Test @Tag("Q3")
    public void registerInstanceAndGetInstancePreciseSignature() {
      Registry registry = new Registry();
      registry.registerInstance(String.class, "hello");
      String instance = registry.getInstance(String.class);
      assertEquals("hello", instance);
    }
    //@Test @Tag("Q3")
    //public void shouldNotCompilePreciseSignature() {
    //  var registry = new Registry();
    //  registry.registerInstance(String.class, 3);
    //}
  }


  @Nested
  class Q4 {
    @Test @Tag("Q4")
    public void registerProvider() {
      record Bar() {}

      var registry = new Registry();
      registry.registerProvider(Bar.class, Bar::new);
      var instance1 = registry.getInstance(Bar.class);
      var instance2 = registry.getInstance(Bar.class);
      assertNotSame(instance1, instance2);
      assertEquals(instance1, instance2);
    }

    @Test @Tag("Q4")
    public void registerProviderWithAnInterface() {
      interface I {
        String hello();
      }
      record Impl() implements I {
        @Override
        public String hello() {
          return "hello";
        }
      }

      var registry = new Registry();
      registry.registerProvider(I.class, Impl::new);
      var instance1 = registry.getInstance(I.class);
      var instance2 = registry.getInstance(I.class);
      assertNotSame(instance1, instance2);
      assertEquals(instance1, instance2);
    }

    @Test @Tag("Q4")
    public void registerProviderPreconditions() {
      var registry = new Registry();
      assertAll(
          () -> assertThrows(NullPointerException.class, () -> registry.registerProvider(null, Object::new)),
          () -> assertThrows(NullPointerException.class, () -> registry.registerInstance(Consumer.class, null))
      );
    }
  }


  @Nested
  class Q5 {
    @Test @Tag("Q5")
    public void findSettersOneInjectMethod() {
      class A {
        @Inject
        public void setValue(String value) {}
      }
      List<Method> setters = Registry.findSetters(A.class);
      assertAll(
          () -> assertEquals(1, setters.size()),
          () -> assertEquals(A.class.getMethod("setValue", String.class), setters.get(0))
      );
    }

    @Test @Tag("Q5")
    public void findSettersNoInjectMethod() {
      class A {
        // No @Inject
        public void setValue(String value) {}
      }
      var setters = Registry.findSetters(A.class);
      assertEquals(List.of(), setters);
    }

    @Test @Tag("Q5")
    public void findSettersNoPublicMethod() {
      class A {
        @Inject
        private void setValue(String value) {}
      }
      var setters = Registry.findSetters(A.class);
      assertEquals(List.of(), setters);
    }

    @Test @Tag("Q5")
    public void findSettersOneInjectAbstractMethod() {
      interface I {
        @Inject
        void setValue(String value);
      }
      var setters = Registry.findSetters(I.class);
      assertAll(
          () -> assertEquals(1, setters.size()),
          () -> assertEquals(I.class.getMethod("setValue", String.class), setters.get(0))
      );
    }

    @Test @Tag("Q5")
    public void findSettersOneInjectDefaultMethod() {
      interface I {
        @Inject
        default void setValue(Integer value) {}
      }
      var setters = Registry.findSetters(I.class);
      assertAll(
          () -> assertEquals(1, setters.size()),
          () -> assertEquals(I.class.getMethod("setValue", Integer.class), setters.get(0))
      );
    }

    @Test @Tag("Q5")
    public void findSettersTwoInjectMethod() throws NoSuchMethodException {
      class A {
        @Inject
        public void setValue1(Double value) {}
        @Inject
        public void setValue2(Double value) {}
      }
      var setters = Registry.findSetters(A.class);
      var methods = Set.of(
          A.class.getMethod("setValue1", Double.class),
          A.class.getMethod("setValue2", Double.class)
      );
      assertAll(
          () -> assertEquals(2, setters.size()),
          () -> assertEquals(methods, new HashSet<>(setters))
      );
    }

    @Test @Tag("Q5")
    public void registerProviderWithSettersInjection() {
      class A {
        private String s;
        private int i;

        @Inject
        public void setString(String s) {
          this.s = s;
        }
        @Inject
        public void setInteger(Integer i) {
          this.i = i;
        }

        // No @Inject
        public void setAnotherInteger(Integer i) {
          fail();
        }
      }

      var registry = new Registry();
      registry.registerProvider(A.class, A::new);
      registry.registerInstance(String.class, "hello");
      registry.registerInstance(Integer.class, 42);
      var a = registry.getInstance(A.class);
      assertAll(
          () -> assertEquals("hello", a.s),
          () -> assertEquals(42, a.i)
      );
    }

    @Test @Tag("Q5")
    public void registerProviderWithSettersWithProviderInjection() {
      class A {
        private int value1;
        private int value2;

        @Inject
        public void setValue1(Integer value1) {
          this.value1 = value1;
        }
        @Inject
        public void setValue2(Integer value2) {
          this.value2 = value2;
        }
      }

      var counter = new Object() { int count; };
      var registry = new Registry();
      registry.registerProvider(A.class, A::new);
      registry.registerProvider(Integer.class, () -> counter.count++);
      var a = registry.getInstance(A.class);
      assertTrue((a.value1 == 0 && a.value2 == 1) ||
                 (a.value2 == 0 && a.value1 == 1));
    }

    @Test @Tag("Q5")
    public void registerProviderWithSettersStaticShouldBeIgnored() {
      interface A {
        static void setValue(String s) {
          fail();
        }
      }

      var counter = new Object() { int count; };
      var registry = new Registry();
      registry.registerInstance(A.class, new A() {});
      registry.registerInstance(String.class, "hello");
      var a = registry.getInstance(A.class);
      assertNotNull(a);
    }

    @Test @Tag("Q5")
    public void registerProviderWithSettersDefaultMethod() {
      interface A {
        @Inject
        default void setValue(String value) {
          assertEquals("hello", value);
          FLAG.set(true);
        }

        ThreadLocal<Boolean> FLAG = ThreadLocal.withInitial(() -> false);
      }

      var registry = new Registry();
      registry.registerInstance(A.class, new A() {});
      registry.registerInstance(String.class, "hello");
      var a = registry.getInstance(A.class);
      assertTrue(A.FLAG.get());
    }
  }


  @Nested
  class Q7 {
    @Test @Tag("Q7")
    public void registerProviderClassWithAnEmptyClass() {
      record A() {
        @Inject
        public A {}
      }

      var registry = new Registry();
      registry.registerProviderClass(A.class, A.class);
      A a = registry.getInstance(A.class);
      assertNotNull(a);
    }

    @Test @Tag("Q7")
    public void registerProviderClassWithAConstructorWithTwoIntegers() {
      record A(Integer value1, Integer value2) {
        @Inject
        public A {}
      }

      var counter = new Object() { int count; };
      var registry = new Registry();
      registry.registerProviderClass(A.class, A.class);
      registry.registerProvider(Integer.class, () -> counter.count++);
      A a = registry.getInstance(A.class);
      assertAll(
          () -> assertEquals(0, a.value1),
          () -> assertEquals(1, a.value2)
      );
    }

    @Test @Tag("Q7")
    public void registerProviderClassNoInjectConstructor() {
      class A {
        // No @Inject
        public A() {}
      }

      var registry = new Registry();
      assertThrows(IllegalStateException.class, () -> registry.registerProviderClass(A.class, A.class));
    }

    @Test @Tag("Q7")
    public void registerProviderClassMultipleInjectConstructors() {
      class A {
        @Inject
        public A() {}

        @Inject
        public A(Boolean b) {}
      }

      var registry = new Registry();
      assertThrows(IllegalStateException.class, () -> registry.registerProviderClass(A.class, A.class));
    }

    @Test @Tag("Q7")
    public void registerProviderClassPreconditions() {
      var registry = new Registry();
      assertAll(
          () -> assertThrows(NullPointerException.class, () -> registry.registerProviderClass(null, Object.class)),
          () -> assertThrows(NullPointerException.class, () -> registry.registerProviderClass(Consumer.class, null))
      );
    }
  }
}