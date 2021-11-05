package com.github.forax.framework.injector;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class AnnotationScanner {
  // package for testing
  static Stream<String> findAllJavaFilesInFolder(Path folder) throws IOException{
    return Files.list(folder)
        .map(path -> path.getFileName().toString())
        .filter(filename -> filename.endsWith(".class"))
        .map(filename -> filename.substring(0, filename.length() - ".class".length()));
  }

  // package for testing
  static List<Class<?>> findAllClasses(String packageName, ClassLoader classLoader) {
    var urls = Utils2.getResources(packageName.replace('.', '/'), classLoader);
    var urlList = Collections.list(urls);
    if (urlList.isEmpty()) {
      throw new IllegalStateException("no folder for package " + packageName + " found");
    }
    return urlList.stream()
        .flatMap(url -> {
          try {
            var folder = Path.of(url.toURI());
            return findAllJavaFilesInFolder(folder)
                .<Class<?>>map(className -> Utils2.loadClass(packageName + '.' + className, classLoader));
          } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("error while looking for classes of package " + packageName, e);
          }
        })
        .toList();
  }

  private final HashMap<Class<?>, Consumer<? super Class<?>>> actionMap = new HashMap<>();

  public void addAction(Class<? extends Annotation> annotationClass, Consumer<? super Class<?>> action) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(action);
    var result = actionMap.putIfAbsent(annotationClass, action);
    if (result != null) {
      throw new IllegalStateException("an action is already registered for annotation " + annotationClass);
    }
  }

  public void scanClassPathPackageForAnnotations(Class<?> classInPackage) {
    Objects.requireNonNull(classInPackage);
    var packageName = classInPackage.getPackageName();
    var classLoader = classInPackage.getClassLoader();
    for(var clazz: findAllClasses(packageName, classLoader)) {
      for (var annotation : clazz.getAnnotations()) {
        actionMap.getOrDefault(annotation.annotationType(), __ -> {}).accept(clazz);
      }
    }
  }
}
