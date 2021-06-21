package com.github.forax.framework.injector;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

final class Utils {
  private Utils() {
    throw new AssertionError();
  }

  public static BeanInfo beanInfo(Class<?> beanType) {
    try {
      return Introspector.getBeanInfo(beanType);
    } catch (IntrospectionException e) {
      throw new IllegalStateException(e);
    }
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

  @SuppressWarnings("unchecked")   // very wrong but works
  private static <T extends Throwable> AssertionError rethrow(Throwable cause) throws T {
    throw (T) cause;
  }
}
