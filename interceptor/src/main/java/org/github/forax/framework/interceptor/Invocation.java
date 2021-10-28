package org.github.forax.framework.interceptor;

import java.lang.reflect.Method;

@FunctionalInterface
public interface Invocation {
  Object invoke(Object instance, Method method, Object[] args) throws Throwable;
}
