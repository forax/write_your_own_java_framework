package org.github.forax.framework.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class InterceptorRegistry {
  private final HashMap<Class<?>, List<Interceptor>> interceptorMap = new HashMap<>();
  private final HashMap<Method, Fun> funCache = new HashMap<>();

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(interceptor);
    interceptorMap.computeIfAbsent(annotationClass, __ -> new ArrayList<>()).add(interceptor);
  }

  private static Object invokeDelegate(Object delegate, Method method, Object[] args) throws Exception {
    if (delegate == null) {
      return Utils.defaultValue(method.getReturnType());
    }
    return Utils.invokeMethod(delegate, method, args);
  }

  @FunctionalInterface
  private interface Fun {
    Object apply(Object proxy, Object[] args, Object delegate) throws Exception;
  }

  private Fun getFun(Stream<Interceptor> interceptors, Method method) {
    return Utils.reverseList(interceptors.toList()).stream()
        .reduce((__, args, delegate) -> invokeDelegate(delegate, method, args),
            (fun, interceptor) -> (proxy, args, delegate) -> interceptor.intercept(method, proxy, args, () -> fun.apply(proxy, args, delegate)),
            (_1, _2) -> { throw null; });
  }

  private Fun getFunFromCache(Method method) {
    return funCache.computeIfAbsent(method, m -> getFun(findInterceptors(m), m));
  }

  public <T> T createProxy(Class<? extends T> type, T delegate) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
        new Class<?>[] { type },
        (proxy, method, args) -> getFunFromCache(method).apply(proxy, args, delegate)));
  }

  // package private
  Stream<Interceptor> findInterceptors(Method method) {
    return Stream.of(
            Arrays.stream(method.getDeclaringClass().getAnnotations()),
            Arrays.stream(method.getAnnotations()),
            Arrays.stream(method.getParameterAnnotations()).flatMap(Arrays::stream))
        .flatMap(s -> s)
        .map(Annotation::annotationType)
        .distinct()
        .flatMap(annotationType -> interceptorMap.getOrDefault(annotationType, List.of()).stream());
  }
}
