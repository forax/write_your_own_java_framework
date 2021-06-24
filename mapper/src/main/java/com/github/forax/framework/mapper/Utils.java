package com.github.forax.framework.mapper;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

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

  public static Object invoke(Object bean, Method method, Object... args) {
    try {
      return method.invoke(bean, args);
    } catch (IllegalArgumentException e) {
      throw new AssertionError(e);
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

  public static <T> T newInstance(Constructor<T> constructor) {
    try {
      return constructor.newInstance();
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

  public static <T> List<T> reverseList(List<T> list) {
    assert list instanceof RandomAccess;
    return new AbstractList<>() {
      @Override
      public T get(int i) {
        return list.get(list.size() - 1 - i);
      }

      @Override
      public int size() {
        return list.size();
      }
    };
  }
}