package org.github.forax.framework.interceptor;

import java.lang.reflect.Method;

public interface AroundAdvice {
  void pre(Object instance, Method method, Object[] args) throws Throwable;
  void post(Object instance, Method method, Object[] args, Object result) throws Throwable;
}
