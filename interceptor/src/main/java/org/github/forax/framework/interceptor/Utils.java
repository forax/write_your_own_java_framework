package org.github.forax.framework.interceptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

final class Utils {
  private Utils() {
    throw new AssertionError();
  }

  private static final Map<String, Object> DEFAULT_VALUES = Map.of(
      "Z", false, "B", (byte) 0, "C", '\0', "S", (short) 0, "I", 0, "J", 0L, "F", 0f, "D", 0.);

  public static Object defaultValue(Class<?> type) {
    return DEFAULT_VALUES.get(type.descriptorString());
  }

  public static Object invokeMethod(Object object, Method method, Object[] args) throws Exception {
    try {
      return method.invoke(object, args);
    } catch (IllegalArgumentException e) {
      throw new AssertionError("can not call " + method + " on " + object + " with " + Arrays.toString(args), e);
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError) new IllegalAccessError().initCause(e);
    } catch (InvocationTargetException e) {
      var cause = e.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new UndeclaredThrowableException(cause);
    }
  }

  static <T> List<T> reverseList(List<T> list) {
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
