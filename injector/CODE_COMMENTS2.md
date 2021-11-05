# Code comments


### Q1

```java
  static Stream<String> findAllJavaFilesInFolder(Path folder) throws IOException{
    return Files.list(folder)
        .map(path -> path.getFileName().toString())
        .filter(filename -> filename.endsWith(".class"))
        .map(filename -> filename.substring(0, filename.length() - ".class".length()));
  }
```


### Q2

```java
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
```


### Q3

```java
  private final HashMap<Class<?>, Consumer<? super Class<?>>> actionMap = new HashMap<>();

  public void addAction(Class<? extends Annotation> annotationClass, Consumer<? super Class<?>> action) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(action);
    var result = actionMap.putIfAbsent(annotationClass, action);
    if (result != null) {
      throw new IllegalStateException("an action is already registered for annotation " + annotationClass);
    }
  }
```


### Q4

```java
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
```
