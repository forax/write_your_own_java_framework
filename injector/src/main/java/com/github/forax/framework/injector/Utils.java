package com.github.forax.framework.injector;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.function.Consumer;

final class Utils {
  private Utils() {
    throw new AssertionError();
  }

  static void invokeSetter(Method setter, Object instance, Object value) {
    try {
      setter.invoke(instance, value);
    } catch (IllegalArgumentException e) {
      throw new AssertionError(e);
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError) new IllegalAccessError().initCause(e);
    } catch (InvocationTargetException e) {
      var cause = e.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new UndeclaredThrowableException(cause);
    }
  }

  static Object invokeConstructor(Constructor<?> constructor, Object[] args) {
    try {
      return constructor.newInstance(args);
    } catch (IllegalArgumentException e) {
      throw new AssertionError(e);
    } catch (InstantiationException e) {
      throw (InstantiationError) new InstantiationError().initCause(e);
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError) new IllegalAccessError().initCause(e);
    } catch (InvocationTargetException e) {
      throw rethrow(e.getCause());
    }
  }

  @SuppressWarnings("unchecked")
  private static AssertionError rethrow(Throwable cause) {
    ((Consumer<Throwable>)(Consumer<?>)(Consumer<RuntimeException>) t -> { throw t; }).accept(cause);
    throw new AssertionError("never reached");
  }
}
