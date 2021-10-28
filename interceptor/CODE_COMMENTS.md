# Code comments

### Q1

```java
public final class InterceptorRegistry {
  private AroundAdvice advice;

  public void addAroundAdvice(Class<? extends Annotation> annotationClass, AroundAdvice advice) {
    Objects.requireNonNull(annotationClass, "annotationClass is null");
    Objects.requireNonNull(advice, "advice is null");
    this.advice = advice;
  }

  public <T> T createProxy(Class<T> type, T instance) {
    Objects.requireNonNull(type, "type is null");
    Objects.requireNonNull(instance, "instance is null");
    return type.cast(
        Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> {
              if (advice != null) {
                advice.pre(instance, method, args);
              }
              var result = Utils.invokeMethod(instance, method, args);
              if (advice != null) {
                advice.post(instance, method, args, result);
              }
              return result;
            }));
  }
}
```

### Q2

```java
public final class InterceptorRegistry {
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
    return type.cast(
        Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> {
              var advices = findAdvices(method);
              for (var advice : advices) {
                advice.pre(instance, method, args);
              }
              var result = Utils.invokeMethod(instance, method, args);
              for (var advice : advices) {
                advice.post(instance, method, args, result);
              }
              return result;
            }));
  }
}
```


### Q3

```java
public final class InterceptorRegistry {
  ...

  private final HashMap<Class<?>, List<Interceptor>> interceptorMap = new HashMap<>();

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass, "annotationClass is null");
    Objects.requireNonNull(interceptor, "interceptor is null");
    interceptorMap.computeIfAbsent(annotationClass, __ -> new ArrayList<>()).add(interceptor);
  }

  // package private
  List<Interceptor> findInterceptors(Method method) {
    return Arrays.stream(method.getAnnotations())
        .flatMap(annotation -> interceptorMap.getOrDefault(annotation.annotationType(), List.of()).stream())
        .toList();
  }
}
```

### Q4

```java
public final class InterceptorRegistry {
  ...
  // package private
  static Invocation getInvocation(List<Interceptor> interceptors) {
    return Utils.reverseList(interceptors).stream()
        .reduce(Utils::invokeMethod,
            (invocation, interceptor) -> (instance, method, args) -> interceptor.intercept(instance, method, args, invocation),
            (_1, _2) -> { throw new AssertionError(); });
  }
}
```

### Q5

```java
public final class InterceptorRegistry {
  private final HashMap<Class<?>, List<Interceptor>> interceptorMap = new HashMap<>();

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
  
  public <T> T createProxy(Class<T> type, T instance) {
    Objects.requireNonNull(type, "type is null");
    Objects.requireNonNull(instance, "instance is null");
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
        new Class<?>[] { type },
        (proxy, method, args) -> getInvocation(findInterceptors(method)).invoke(instance, method, args)));
  }
}
```

### Q6

We can now add a cache, again using `computeIfAbsent()`, so calling the same method even with different proxies
always return the same instance of `Invocation`.
We also need to invalidate the cache each time `addInterceptor()` is called.

```java
public final class InterceptorRegistry {
  private final HashMap<Class<?>, List<Interceptor>> interceptorMap = new HashMap<>();
  private final HashMap<Method, Invocation> invocationCache = new HashMap<>();

  ...
  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass, "annotationClass is null");
    Objects.requireNonNull(interceptor, "interceptor is null");
    interceptorMap.computeIfAbsent(annotationClass, __ -> new ArrayList<>()).add(interceptor);
    invocationCache.clear();
  }

  ...
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

```


### Q7

To support annotation from the declaring interface, the method or a parameter of the method,
we create a Stream of the three Streams and we flatMap it.
We use `distinct` here, because the same interceptor can be registered on different annotations
but we want to call it once.

```java
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
```