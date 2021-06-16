package com.github.forax.framework.injector;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOError;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;

import static org.junit.jupiter.api.Assertions.*;

public class UtilsTest {
  @Test
  @Tag("Q5")
  public void invokeSetter() throws NoSuchMethodException {
    class A {
      private String color;

      public void setColor(String color) {
        this.color = color;
      }
    }

    var method = A.class.getMethod("setColor", String.class);
    var a = new A();
    Utils.invokeSetter(method, a, "green");
    assertEquals("green", a.color);
  }
  @Test @Tag("Q5")
  public void invokeSetterBadArgument() throws NoSuchMethodException {
    class A {
      public void setInteger(Integer value) {
        fail();
      }
    }

    var method = A.class.getMethod("setInteger", Integer.class);
    var a = new A();
    assertThrows(AssertionError.class, () -> Utils.invokeSetter(method, a, "notAnInteger"));
  }
  @Test @Tag("Q5")
  public void invokeSetterThrowUncheckedException() throws NoSuchMethodException {
    interface Foo { }
    class A {
      public void setFoo(Foo foo) {
        throw new IllegalArgumentException("bad foo");
      }
    }

    var method = A.class.getMethod("setFoo", Foo.class);
    assertThrows(IllegalArgumentException.class, () -> Utils.invokeSetter(method, new A(), new Foo() {}));
  }
  @Test @Tag("Q5")
  public void invokeSetterCheckedException() throws NoSuchMethodException {
    class A {
      public void setString(String s) throws IOException {
        throw new IOException("an error occurs");
      }
    }

    var method = A.class.getMethod("setString", String.class);
    assertThrows(UndeclaredThrowableException.class, () -> Utils.invokeSetter(method, new A(), "hello"));
  }
  @Test @Tag("Q5")
  public void invokeSetterCheckedError() throws NoSuchMethodException {
    class A {
      public void setString(String s){
        throw new IOError(null);
      }
    }

    var method = A.class.getMethod("setString", String.class);
    assertThrows(IOError.class, () -> Utils.invokeSetter(method, new A(), "hello"));
  }


  @Test @Tag("Q7")
  public void invokeConstructor() throws NoSuchMethodException {
    record A() {
      @Inject
      public A {}
    }

    Object object = Utils.invokeConstructor(A.class.getConstructor(), new Object[0]);
    assertTrue(object instanceof A);
  }
  @Test @Tag("Q7")
  public void invokeConstructorWithArguments() throws NoSuchMethodException {
    record A(String s, int i) {
      public A {
        assertEquals("foo", s);
        assertEquals(42, i);
      }
    }

    var object = Utils.invokeConstructor(A.class.getConstructor(String.class, int.class), new Object[] { "foo", 42});
    assertTrue(object instanceof A);
  }
}