package com.github.forax.framework.injector;

import com.sun.source.tree.Tree;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

public final class InjectorRegistry {
  public InjectorRegistry() { }

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

  private void initInstance(Object instance, List<PropertyDescriptor> properties) {
    for(var property: properties) {
      var setter = property.getWriteMethod();
      var value = lookupInstance(property.getPropertyType());
      Utils.invokeMethod(instance, setter, value);
    }
  }

  private static Optional<Constructor<?>> findConstructorAnnotatedWithInject(Class<?> providerClass) {
    var constructors =  Arrays.stream(providerClass.getConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
        .toList();
    return switch(constructors.size()) {
      case 0 -> Optional.empty();
      case 1 -> Optional.of(constructors.getFirst());
      default -> throw new IllegalStateException("more than one constructor annotated with @Inject in " + providerClass.getName());
    };
  }

  public <T> void registerProviderClass(Class<T> type, Class<? extends T> providerClass) {
    requireNonNull(type);
    requireNonNull(providerClass);
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

  public void registerProviderClass(Class<?> providerClass) {
    registerProviderClassImpl(providerClass);
  }

  private <T> void registerProviderClassImpl(Class<T> providerClass) {
    registerProviderClass(providerClass, providerClass);
  }
}
