

Q1

```java
  private final HashMap<Class<?>, Interceptor> interceptorMap = new HashMap<>();

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(interceptor);
    interceptorMap.put(annotationClass, interceptor);
  }

  public <T> T createProxy(Class<? extends T> type, T delegate) {
    if (!(type.isInterface())) {
      throw new IllegalArgumentException("type " + type.getName() + " is not an interface");
    }
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type}, (proxy, method, args) -> {
      var interceptor = interceptorMap.values().stream().findFirst().orElseThrow();
      return interceptor.intercept(method, proxy, args, null);
    }));
  }
```

Q2

```java
  // package private for testing
  Stream<Interceptor> findInterceptors(Method method) {
     return Arrays.stream(method.getAnnotations())
         .flatMap(ann -> Stream.ofNullable(interceptorMap.get(ann.annotationType())));
   }
```

Q3

```java
  private static Object invokeDelegate(Object delegate, Method method, Object[] args) throws Exception {
    if (delegate == null) {
      return Utils.defaultValue(method.getReturnType());
    }
    return Utils.invokeMethod(delegate, method, args);
  }

  private Callable<?> getCallable(Stream<Interceptor> interceptors, Method method, Object proxy, Object[] args, Object delegate) {
    Callable<?> callable = () -> invokeDelegate(delegate, method, args);
    for(var interceptor: Utils.reverseList(interceptors.toList())) {
      var callable2 = callable;
      callable = () -> interceptor.intercept(method, proxy, args, callable2);
    }
    return callable;
  }

  public <T> T createProxy(Class<? extends T> type, T delegate) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
      var interceptors = findInterceptors(method);
      return getCallable(interceptors, method, proxy, args, delegate).call();
    }));
  }
```

Q4

```java
  private final HashMap<Class<?>, List<Interceptor>> interceptorMap = new HashMap<>();

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(interceptor);
    interceptorMap.computeIfAbsent(annotationClass, __ -> new ArrayList<>()).add(interceptor);
  }

  // package private
  Stream<Interceptor> findInterceptors(Method method) {
    return Arrays.stream(method.getAnnotations())
        .flatMap(ann -> interceptorMap.getOrDefault(ann.annotationType(), List.of()).stream());
  }

  private static Object invokeDelegate(Object delegate, Method method, Object[] args) throws Exception {
    if (delegate == null) {
      return Utils.defaultValue(method.getReturnType());
    }
    return Utils.invokeMethod(delegate, method, args);
  }

  private Callable<?> getCallable(Stream<Interceptor> interceptors, Method method, Object proxy, Object[] args, Object delegate) {
    Callable<?> callable = () -> invokeDelegate(delegate, method, args);
    for(var interceptor: Utils.reverseList(interceptors.toList())) {
      var callable2 = callable;
      callable = () -> interceptor.intercept(method, proxy, args, callable2);
    }
    return callable;
  }

  public <T> T createProxy(Class<? extends T> type, T delegate) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
      var interceptors = findInterceptors(method);
      return getCallable(interceptors, method, proxy, args, delegate).call();
    }));
  }
```

Q5

```java
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
    //Fun fun = (proxy, args, delegate) -> invokeMethod(method, proxy, args, delegate);
    //for(var interceptor: reverse(interceptors.toList())) {
    //  var fun2 = fun;
    //  fun = (proxy, args, delegate) -> interceptor.intercept(method, proxy, args, () -> fun2.apply(proxy, args, delegate));
    //}
    //return fun;

    return Utils.reverseList(interceptors.toList()).stream()
        .reduce((__, args, delegate) -> invokeDelegate(delegate, method, args),
            (fun, interceptor) -> (proxy, args, delegate) -> interceptor.intercept(method, proxy, args, () -> fun.apply(proxy, args, delegate)),
            (_1, _2) -> { throw null; });
  }


  public <T> T createProxy(Class<? extends T> type, T delegate) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type}, (proxy, method, args) -> {
      var interceptors = findInterceptors(method);
      return getFun(interceptors, method).apply(proxy, args, delegate);
    }));
  }
```

Q6

```java
  private final HashMap<Method, Fun> funCache = new HashMap<>();

  private Fun getFunFromCache(Method method) {
    return funCache.computeIfAbsent(method, m -> getFun(findInterceptors(m), m));
  }

  public <T> T createProxy(Class<? extends T> type, T delegate) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
        new Class<?>[] { type },
        (proxy, method, args) -> getFunFromCache(method).apply(proxy, args, delegate)));
  }
```

Q7

```java
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
```