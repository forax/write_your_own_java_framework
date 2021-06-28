package com.github.forax.framework.injector;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class AnnotationScanner {
  // Q1
  static Stream<Class<?>> findAllClasses(String packageName, ClassLoader classLoader) {
    var urls = Utils2.getResources(packageName.replace('.', '/'), classLoader);
    var urlList = Collections.list(urls);
    if (urlList.isEmpty()) {
      throw new IllegalStateException("no folder for package " + packageName + " found");
    }
    return urlList.stream()
        .flatMap(url -> {
          try {
            var folder = Path.of(url.toURI());
            return Files.list(folder)
                .map(path -> path.getFileName().toString())
                .filter(filename -> filename.endsWith(".class"))
                .map(filename -> {
                  var className = filename.substring(0, filename.length() - ".class".length());
                  return Utils2.loadClass(packageName + '.' + className, classLoader);
                });
          } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("error while looking for classes of package " + packageName, e);
          }
        });
  }

  // Q2

  private static boolean isValidClass(Class<?> clazz) {
    return !clazz.isInterface()
        && !Modifier.isAbstract(clazz.getModifiers())
        && !clazz.isMemberClass()
        && !clazz.isLocalClass()
        && !clazz.isAnonymousClass();
  }

  static boolean isAProviderClass(Class<?> clazz) {
    return isValidClass(clazz)
        && Stream.of(clazz.getConstructors(), clazz.getMethods())
            .flatMap(Arrays::stream)
            .anyMatch(executable -> executable.isAnnotationPresent(Inject.class));
  }

  // Q3
  static List<Class<?>> dependencies(Class<?> type) {
    return Stream.of(type.getConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
        .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
        .toList();
  }

  private static void addDependenciesFirst(Class<?> type, LinkedHashSet<Class<?>> set, HashSet<Class<?>> visited) {
    if (!visited.add(type)) {
      return;
    }
    for(var dependency: dependencies(type)) {
      addDependenciesFirst(dependency, set, visited);
    }
    set.add(type);
  }

  // Q4
  static Set<Class<?>> findDependenciesInOrder(List<Class<?>> classes) {
    var set = new LinkedHashSet<Class<?>>();
    var visited = new HashSet<Class<?>>();
    for (Class<?> clazz : classes) {
      visited.add(clazz);
      if (!isAProviderClass(clazz)) {
        continue;
      }
      for(var dependency: dependencies(clazz)) {
        addDependenciesFirst(dependency, set, visited);
      }
      set.add(clazz);
    }
    return set;
  }

  private static <T> void registerProviderClass(InjectorRegistry registry, Class<T> clazz) {
    registry.registerProviderClass(clazz, clazz);
  }

  // Q5
  public static void scanClassPathPackageForAnnotations(InjectorRegistry registry, Class<?> classInPackage) {
    Objects.requireNonNull(registry);
    Objects.requireNonNull(classInPackage);
    var packageName = classInPackage.getPackageName();
    var classLoader = classInPackage.getClassLoader();
    List<Class<?>> classes;
    try(var stream = findAllClasses(packageName, classLoader)) {
      classes = stream.sorted(Comparator.comparing(Class::getName)).toList();
    }
    var dependencies = findDependenciesInOrder(classes);
    dependencies.forEach(clazz -> registerProviderClass(registry, clazz));
  }
}
