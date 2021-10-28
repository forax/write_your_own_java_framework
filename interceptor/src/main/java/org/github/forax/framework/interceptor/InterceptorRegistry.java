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
  /*
  private final HashMap<Class<?>, List<AroundAdvice>> adviceMap = new HashMap<>();

  public void addAroundAdvice(Class<? extends Annotation> annotationClass, AroundAdvice advice) {
    Objects.requireNonNull(annotationClass, "annotationClass is null");
    Objects.requireNonNull(advice, "advice is null");
    adviceMap.computeIfAbsent(annotationClass, __ -> new ArrayList<>()).add(advice);
  }

  // package private for test
  List<AroundAdvice> findAdvices(Method method) {
    return Arrays.stream(method.getAnnotations())
        .flatMap(annotation -> adviceMap.getOrDefault(annotation.annotationType(), List.of()).stream())
        .toList();
  }

  public <T> T createProxy(Class<T> type, T instance) {
    Objects.requireNonNull(type, "type is null");
    Objects.requireNonNull(instance, "instance is null");
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
        new Class<?>[] { type },
        (proxy, method, args) -> {
          var advices = findAdvices(method);
          for (var advice: advices) {
            advice.pre(instance, method, args);
          }
          var result = Utils.invokeMethod(instance, method, args);
          for (var advice: advices) {
            advice.post(instance, method, args, result);
          }
          return result;
        }));
  }*/


  private final HashMap<Class<?>, List<Interceptor>> interceptorMap = new HashMap<>();
  private final HashMap<Method, Invocation> invocationCache = new HashMap<>();

  public void addAroundAdvice(Class<? extends Annotation> annotationClass, AroundAdvice advice) {
    Objects.requireNonNull(annotationClass, "annotationClass is null");
    Objects.requireNonNull(advice, "advice is null");
    addInterceptor(annotationClass, (instance, method, args, invocation) -> {
      advice.pre(instance, method, args);
      var result = invocation.invoke(instance, method, args);
      advice.post(instance, method, args, result);
      return result;
    });
  }

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass, "annotationClass is null");
    Objects.requireNonNull(interceptor, "interceptor is null");
    interceptorMap.computeIfAbsent(annotationClass, __ -> new ArrayList<>()).add(interceptor);
    invocationCache.clear();
  }

  // package private
  List<Interceptor> findInterceptors(Method method) {
    return Stream.of(
            Arrays.stream(method.getDeclaringClass().getAnnotations()),
            Arrays.stream(method.getAnnotations()),
            Arrays.stream(method.getParameterAnnotations()).flatMap(Arrays::stream))
        .flatMap(s -> s)
        .map(Annotation::annotationType)
        .distinct()
        .flatMap(annotationType -> interceptorMap.getOrDefault(annotationType, List.of()).stream())
        .toList();
  }

  // package private
  static Invocation getInvocation(List<Interceptor> interceptors) {
    return Utils.reverseList(interceptors).stream()
        .reduce(Utils::invokeMethod,
            (invocation, interceptor) -> (instance, method, args) -> interceptor.intercept(instance, method, args, invocation),
            (_1, _2) -> { throw new AssertionError(); });
  }

  private Invocation getInvocationFromCache(Method method) {
    return invocationCache.computeIfAbsent(method, m -> getInvocation(findInterceptors(m)));
  }

  public <T> T createProxy(Class<T> type, T instance) {
    Objects.requireNonNull(type, "type is null");
    Objects.requireNonNull(instance, "instance is null");
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
        new Class<?>[] { type },
        (proxy, method, args) -> getInvocationFromCache(method).invoke(instance, method, args)));
  }
}
