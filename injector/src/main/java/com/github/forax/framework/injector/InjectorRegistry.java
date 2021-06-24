package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class InjectorRegistry {
  // Q1

  //private final HashMap<Class<?>, Object> map = new HashMap<>();

  public InjectorRegistry() { }

  /*
  // Q2
  public void registerInstance(Class<?> type, Object instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);
    var result = map.putIfAbsent(type, instance);
    if (result != null) {
      throw new IllegalStateException("instance of " + type.getName() + " already registered");
    }
  }

  public Object getInstance(Class<?> type) {
    var instance = map.get(type);
    if (instance == null) {
      throw new IllegalStateException("no instance of " + type.getName());
    }
    return instance;
  }*/


  /*
  // Q3
  public <T> void registerInstance(Class<T> type, T instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);
    var result = map.putIfAbsent(type, instance);
    if (result != null) {
      throw new IllegalStateException("instance of " + type.getName() + " already registered");
    }
  }

  public <T> T getInstance(Class<T> type) {
    var instance = map.get(type);
    if (instance == null) {
      throw new IllegalStateException("no instance of " + type.getName());
    }
    return type.cast(instance);
  }
  */

  // Q4
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

  /*
  public <T> T getInstance(Class<T> type) {
    var provider = map.get(type);
    if (provider == null) {
      throw new IllegalStateException("no provider of " + type.getName());
    }
    return type.cast(provider.get());
  }*/

  // Q5
  // package private for test
  /*
  static List<Method> findSetters(Class<?> type) {
    var beanInfo = Utils.beanInfo(type);
    return Arrays.stream(beanInfo.getPropertyDescriptors())
       .map(PropertyDescriptor::getWriteMethod)
       .filter(setter -> setter != null && setter.isAnnotationPresent(Inject.class))
       .toList();
  }*/

  // package private for test

  public <T> T getInstance(Class<T> type) {
    var provider = map.get(type);
    if (provider == null) {
      throw new IllegalStateException("no provider of " + type.getName());
    }
    var instance = type.cast(provider.get());
    for(var setter: findSetters(type)) {
      var parameterType = setter.getParameterTypes()[0];
      Utils.invokeSetter(setter, instance, getInstance(parameterType));
    }
    return instance;
  }

  // Q6
  private static final ClassValue<List<Method>> SETTERS_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected List<Method> computeValue(Class<?> type) {
      var beanInfo = Utils.beanInfo(type);
      return Arrays.stream(beanInfo.getPropertyDescriptors())
          .map(PropertyDescriptor::getWriteMethod)
          .filter(setter -> setter != null && setter.isAnnotationPresent(Inject.class))
          .toList();
    }
  };

  static List<Method> findSetters(Class<?> type) {
    return SETTERS_CLASS_VALUE.get(type);
  }

  // Q7 BONUS

  private static final ClassValue<Constructor<?>> CONSTRUCTOR_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected Constructor<?> computeValue(Class<?> type) {
      var constructors = Arrays.stream(type.getConstructors())
          .filter(c -> c.isAnnotationPresent(Inject.class))
          .toList();
      return switch (constructors.size()) {
        case 0 -> throw new IllegalStateException("no public constructor annotated with @Inject");
        case 1 -> constructors.get(0);
        default -> throw new IllegalStateException("more than one public constructor annotated with @Inject");
      };
    }
  };

  public <T> void registerProviderClass(Class<T> type, Class<? extends T> providerClass) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(providerClass);
    var constructor = CONSTRUCTOR_CLASS_VALUE.get(providerClass);
    registerProvider(type, () -> {
      var args = Arrays.stream(constructor.getParameterTypes()).map(this::getInstance).toArray();
      return type.cast(Utils.invokeConstructor(constructor, args));
    });
  }
}
