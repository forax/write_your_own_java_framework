# Code comments

### Q1

We declare `interceptorMap` as a `HashMap`, given that it is declared `private`, it's fine to use the implementation
instead of the interface `Map` here, because it's not visible from the outside.
`addInterceptor(annotationClass, interceptor)` takes a `Class<? extends Annotation>` as first parameter so
if the class is not an annotation, it will not compile.
We also check that the argument are not `null`, storing null is a sure way to go to hell.

```java
  private final HashMap<Class<?>, Interceptor> interceptorMap = new HashMap<>();

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(interceptor);
    interceptorMap.put(annotationClass, interceptor);
  }
```

`createProxy(type, delegate)` use a type parameter `T` to ask the compiler to verify that the type of the class
and the type of the `delegate` are the same. We verify that the Class is an interface.
We then create a proxy, using the interface classloader in order to create a proxy of that interface.
The [InvocationHandler](../COMPANION.md#invocationhandler-proxynewproxyinstance) first find one interceptor
then call it with the method, the proxy and the arguments (and null as `proceed`).
We use [Class.cast()](https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/lang/Class.html#cast(java.lang.Object))
to avoid an unsafe cast to `T`.

```java
  public <T> T createProxy(Class<T> type, T delegate) {
    if (!(type.isInterface())) {
      throw new IllegalArgumentException("type " + type.getName() + " is not an interface");
    }
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type}, (proxy, method, args) -> {
      var interceptor = interceptorMap.values().stream().findFirst().orElseThrow();
      return interceptor.intercept(method, proxy, args, null);
    }));
  }
```


### Q2

Finding all the interceptors for a method is equivalent to asking for all
[annotations](../COMPANION.md#classgetmethod-classgetmethods-classgetconstructors)
and then for each of then get the interceptor corresponding to the annotation type

```java
  // package private for testing
  Stream<Interceptor> findInterceptors(Method method) {
     return Arrays.stream(method.getAnnotations())
         .flatMap(ann -> Stream.ofNullable(interceptorMap.get(ann.annotationType())));
   }
```


### Q3

`invokeDelegate(delegate, method args)` calls `Utils.defaultValue()` or `Utils.invokeMethod()`
depending on if there is a `delegate` or not.

```java
  private static Object invokeDelegate(Object delegate, Method method, Object[] args) throws Exception {
    if (delegate == null) {
      return Utils.defaultValue(method.getReturnType());
    }
    return Utils.invokeMethod(delegate, method, args);
  }
```

For `getCallable(interceptors, method, proxy, args, delegate)`, we first create the last `Callable`,
the one that calls `invokeDelegate()`.
Then we loop over the interceptors in reverse order (using `Utils.reverseList()`) and create a new `Callable`
that will call the previous one.

```java
  private Callable<?> getCallable(Stream<Interceptor> interceptors, Method method, Object proxy, Object[] args, Object delegate) {
    Callable<?> callable = () -> invokeDelegate(delegate, method, args);
    for(var interceptor: Utils.reverseList(interceptors.toList())) {
      var callable2 = callable;
      callable = () -> interceptor.intercept(method, proxy, args, callable2);
    }
    return callable;
  }
```

Then we rewrite `createProxy()` to use `findInterceptor()` and `getCallable()`.

```java
  public <T> T createProxy(Class<T> type, T delegate) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
      var interceptors = findInterceptors(method);
      return getCallable(interceptors, method, proxy, args, delegate).call();
    }));
  }
```

### Q4

We change the `HashMap` to store a list of interceptors.
Inside `addInterceptor`, we use `computeIfAbsent()` to create the list (as an `ArrayList`) if it does not exist.

```java
  private final HashMap<Class<?>, List<Interceptor>> interceptorMap = new HashMap<>();

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(interceptor);
    interceptorMap.computeIfAbsent(annotationClass, __ -> new ArrayList<>()).add(interceptor);
  }
```

We need to change a bit the code of `findInterceptor(method)`.
Here we use `Map.getOrDefault()` instead of `Map.get()` to avoid getting `null` if there is no interceptor
for an annotation.

```java
  // package private
  Stream<Interceptor> findInterceptors(Method method) {
    return Arrays.stream(method.getAnnotations())
        .flatMap(ann -> interceptorMap.getOrDefault(ann.annotationType(), List.of()).stream());
  }
```

### Q5

`Fun` is a private functional interface that takes the proxy, the arguments and the delegate.

```java
  @FunctionalInterface
  private interface Fun {
    Object apply(Object proxy, Object[] args, Object delegate) throws Exception;
  }
```

The code of `getFun()` is the same as the code of `getCallable` but with the proxy, args et delegate as parameter.

```java
  private Fun getFun(Stream<Interceptor> interceptors, Method method) {
    Fun fun = (proxy, args, delegate) -> invokeMethod(method, proxy, args, delegate);
    for(var interceptor: reverse(interceptors.toList())) {
      var fun2 = fun;
      fun = (proxy, args, delegate) -> interceptor.intercept(method, proxy, args, () -> fun2.apply(proxy, args, delegate));
    }
    return fun;
  }
```

An astute reader will have notice that this is just a `reduce`, so it can be written like this
The last parameter of `reduce` is only call with parallel streams, hence the `AssertionError`.

```java
  private Fun getFun(Stream<Interceptor> interceptors, Method method) {
    return Utils.reverseList(interceptors.toList()).stream()
        .reduce((__, args, delegate) -> invokeDelegate(delegate, method, args),
            (fun, interceptor) -> (proxy, args, delegate) -> interceptor.intercept(method, proxy, args, () -> fun.apply(proxy, args, delegate)),
            (_1, _2) -> { throw new AssertionError(); });
  }
```

`createProxy()` needs to be adjusted to call `getFun(interceptors, method)`.

```java
  public <T> T createProxy(Class<T> type, T delegate) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type}, (proxy, method, args) -> {
      var interceptors = findInterceptors(method);
      return getFun(interceptors, method).apply(proxy, args, delegate);
    }));
  }
```


### Q6

We can now add a cache, again using `computeIfAbsent()`, so calling the same method even with different proxies
always return the same instance of `Fun` (i'm not even sorry for the pun).
We also need to invalidate the cache each time `addInterceptor()` is called.

```java
  private final HashMap<Method, Fun> funCache = new HashMap<>();

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(interceptor);
    interceptorMap.computeIfAbsent(annotationClass, __ -> new ArrayList<>()).add(interceptor);
    funCache.clear();
  }

  private Fun getFunFromCache(Method method) {
    return funCache.computeIfAbsent(method, m -> getFun(findInterceptors(m), m));
  }

  public <T> T createProxy(Class<T> type, T delegate) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
        new Class<?>[] { type },
        (proxy, method, args) -> getFunFromCache(method).apply(proxy, args, delegate)));
  }
```


### Q7

To support annotation from the declaring interface, the method or a parameter of the method,
we create a Stream of the three Streams and we flatMap it.
We use `distinct` here, because the same interceptor can be registered on different annotations
but we want to call it once.

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