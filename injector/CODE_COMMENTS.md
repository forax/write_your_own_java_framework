# Code comments


### Q1

```java
public final class InjectorRegistry {
  private final HashMap<Class<?>, Object> map = new HashMap<>();

  public InjectorRegistry() {}

  public void registerInstance(Class<?> type, Object instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);
    var result = map.putIfAbsent(type, instance);
    if (result != null) {
      throw new IllegalStateException("instance of " + type.getName() + " already registered");
    }
  }

  public Object lookupInstance(Class<?> type) {
    Objects.requireNonNull(type);
    var instance = map.get(type);
    if (instance == null) {
      throw new IllegalStateException("no instance of " + type.getName());
    }
    return instance;
  }
}
```


### Q2

```java
public final class InjectorRegistry {
  private final HashMap<Class<?>, Object> map = new HashMap<>();

  public InjectorRegistry() {}

  public <T> void registerInstance(Class<T> type, T instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);
    var result = map.putIfAbsent(type, instance);
    if (result != null) {
      throw new IllegalStateException("instance of " + type.getName() + " already registered");
    }
  }

  public <T> T lookupInstance(Class<T> type) {
    Objects.requireNonNull(type);
    var instance = map.get(type);
    if (instance == null) {
      throw new IllegalStateException("no instance of " + type.getName());
    }
    return type.cast(instance);
  }
}  
```


### Q3

```java
public final class InjectorRegistry {
  private final HashMap<Class<?>, Supplier<?>> map = new HashMap<>();

  public <T> void registerInstance(Class<T> type, T instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);
    registerProvider(type, () -> instance);
  }

  public <T> void registerProvider(Class<T> type, Supplier<? extends T> provider) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(provider);
    var result = map.putIfAbsent(type, provider);
    if (result != null) {
      throw new IllegalStateException("provider of " + type.getName() + " already registered");
    }
  }

  private Supplier<?> lookupProvider(Class<?> type) {
    var provider = map.get(type);
    if (provider == null) {
      throw new IllegalStateException("no provider of " + type.getName());
    }
    return provider;
  }
  
  public <T> T lookupInstance(Class<T> type) {
    Objects.requireNonNull(type);
    var provider = lookupProvider(type);
    return type.cast(provider.get());
  }
}  
```


### Q4

```java
  // package private for testing
  static List<PropertyDescriptor> findInjectableProperties(Class<?> type) {
    var beanInfo = Utils.beanInfo(type);
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        .filter(property -> {
          var setter = property.getWriteMethod();
          return setter != null && setter.isAnnotationPresent(Inject.class);
        })
        .toList();
  }
```


### Q5

```java
  private void initInstance(Object instance, List<PropertyDescriptor> properties) {
    for(var property: properties) {
      var setter = property.getWriteMethod();
      var value = lookupInstance(property.getPropertyType());
      Utils.invokeMethod(instance, setter, value);
    }
  }
    
  public <T> void registerProviderClass(Class<T> type, Class<? extends T> providerClass) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(providerClass);
    var properties = findInjectableProperties(providerClass);
    var constructor = Utils.defaultConstructor(providerClass);
    registerProvider(type, () -> {
      var instance = Utils.newInstance(constructor);
      initInstance(instance, properties);
      return instance;
    });
  }
```


### Q6

```java
  private static Optional<Constructor<?>> findConstructorAnnotatedWithInject(Class<?> providerClass) {
    var constructors =  Arrays.stream(providerClass.getConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
        .toList();
    return switch(constructors.size()) {
      case 0 -> Optional.empty();
      case 1 -> Optional.of(constructors.get(0));
      default -> throw new IllegalStateException("more than one constructor annotated with @Inject in " + providerClass.getName());
    };
  }

  public <T> void registerProviderClass(Class<T> type, Class<? extends T> providerClass) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(providerClass);
    var properties = findInjectableProperties(providerClass);
    var constructor = findConstructorAnnotatedWithInject(providerClass)
        .orElseGet(() -> Utils.defaultConstructor(providerClass));
    var parameterTypes = constructor.getParameterTypes();
    registerProvider(type, () -> {
      Object[] args = Arrays.stream(parameterTypes)
          .map(this::lookupInstance)
          .toArray();
      var instance = type.cast(Utils.newInstance(constructor, args));
      initInstance(instance, properties);
      return instance;
    });
  }
```


### Q7

```java
  public void registerProviderClass(Class<?> providerClass) {
    registerProviderClassImpl(providerClass);
  }

  private <T> void registerProviderClassImpl(Class<T> providerClass) {
    registerProviderClass(providerClass, providerClass);
  }
```