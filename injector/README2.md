# Annotation classpath scanning

1. We want to implement the classpath scanning, i.e. find all classes of a package
   from its different folders and register the class that have at least a constructor or a method
   annotated with `@Inject` as provider class.

   To indicate which package to scan, the method `scanClassPathPackageForAnnotations(class)`
   takes a class as parameter instead of a package name. This is trick provides both the
   [package name](https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/lang/Class.html#getPackageName())
   and the
   [class loader](https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/lang/Class.html#getClassLoader()).

   For the implementation,
   [ClassLoader.getResources()](https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/lang/ClassLoader.html#getResources(java.lang.String))
   with as parameter a package name with the dots ('.') replaced by slash ('/')  returns all the `URL`s
   containing the classes (you can have more than one folder, by example one folder in src and one folder in test).
   Calling `Path.of(url.toURI())` get a `Path` from an `URL` and
   [Class.forName(name, /*initialize=*/ false, classloader)](https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader))
   loads a `Class`without initializing the class (without running its static block).

   Implement the method `scanClassPathPackageForAnnotations(class)` and
   check that the tests in the nested class "Q7" all pass.


   