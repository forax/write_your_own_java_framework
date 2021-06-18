package com.github.forax.framework.mapper;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

  public static Object invoke(Object bean, Method getter) {
    try {
      return getter.invoke(bean);
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
}