---

## Null Object Design Pattern

```java
private static Object NULL_OBJECT = new Object();

private void method(Object value) {
  if (value == NULL_OBJECT) {
    ...
  }  
}
```

## ThreadLocal

```java
private static final ThreadLocal<Data> DATA_THREAD_LOCAL = new ThreadLocal<>();

void method() {
  Data data = ...
    DATA_THREAD_LOCAL.set(data);
  try {
    ...
  } finally{
    DATA_THREAD_LOCAL.remove();
  }
}
```

## ClassValue

```java
private static final ClassValue<DATA> DATA_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected Data computeValue(Class<?> type) {
      Data data = ...
      return data;
    }
  };

void method(Class<?> type) {
  Data data = DATA_CLASS_VALUE.get(type);
  ...
}
```


## Reflection

### CLass.getMethod(), Class.getMethods(), Class.getConstructors()

```java
  Method method;
  try {
    method String.class.getMethod("concat", String.class);
  } catch(NoSuchMethodException e) {
    throw (NoSuchMethodError) new NoSuchMethodError().initCause(e);
  }
```

```java
  Method[] methods;
  try {
    methods = String.class.getMethods();
  } catch(NoSuchMethodException e) {
    throw (NoSuchMethodError) new NoSuchMethodError().initCause(e);
    }
```

### Method.invoke(), Constructor.newInstance()

```java
void invokeMethod(Method setter, Object instance, Object[] args) {
  try {
    return method.invoke(instance, value);
  } catch (IllegalArgumentException e) {
    throw new AssertionError(e);
  } catch (IllegalAccessException e) {
    throw (IllegalAccessError) new IllegalAccessError().initCause(e);
  } catch (InvocationTargetException e) {
    throw rethrow(e.getCause());
  }
}

@SuppressWarnings("unchecked")   // very wrong but works
static <T extends Throwable> AssertionError rethrow(Throwable cause) throws T {
  throw (T) cause;
}
```

```java
Object newInstance(Constructor<?> constructor, Object... args) {
  try {
    return constructor.newInstance(args);
  }  catch (IllegalArgumentException e) {
    throw new AssertionError(e);
  } catch (InstantiationException e) {
    throw (InstantiationError) new InstantiationError().initCause(e);
  } catch (IllegalAccessException e) {
    throw (IllegalAccessError) new IllegalAccessError().initCause(e);
  } catch (InvocationTargetException e) {
    throw rethrow(e.getCause());
  }
}
```

### InvocationHandler, Proxy.newProxyInstance(), Proxy.isProxy() and Proxy.getInvocationHandler()


```java
InvocationHandler invocationHandler =
    (Object proxy, Method method, Object[] args) -> {
      ...
    };
Object proxy = Proxy.newProxyInstance(type.getClassLoader(),
    new Class<?>[] { type },
    invocationHandler);
```

```java
import java.lang.reflect.Proxy;

class MyInvocationHandler implements InvocationHandler {
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    ...
  }
}

void method(Object o) {
  if (Proxy.isProxyClass(o.getClass())) {
    ...
  }
  InvocationHandler invocationHandler = Proxy.getInvocationHandler(o);
  if (invocationHandler instanceof MyInvocationHandler myInvocationHandler) {
    ...
  }
}
```


## Annotation

### Declaration and meta-annotation

```java
@interface MyAnnotation { 
  String value();
}
```

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@interface Marker {
}
```


### Method.isAnnotationPresent(), Method.getAnnotation(), Method.getAnnotations()

```java
void method(Class<?> type) {
  boolean isPresent = type.isAnnotationPresent(Marker.class);
}
```

```java
void method(Method method) {
  MyAnnitation myAnnotation = method.getAnnotation(MyAnnitation.class);
  if (myAnnotation != null) {
    String value = myAnnotation.value();
    ...
  }
}
```

```java
void method(Constructor<?> constructor) {
  Annotation[] annotations = constructor.getAnnotations();
  ...
}
```

## Java Compiler generics attributes

```java
void method(Class<?> type) {
  Type[]  genericInterfaces = type.getGenericInterfaces();
  for(Type genericInterface: genericInterfaces) {
    switch(genericInterface) {
      case ParameterizedType parameterizedType -> {   // e.g. List<String>
        Class<?> rawType = (Class<?>) parameterizedType.getRawType();
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        ...
      }
      case GenericArrayType genericArrayType -> ...  // e.g. List<String>[]
      case TypeVariable<?> typeVariable -> ...       // e.g T
      case WildcardType wildcardType -> ...          // e.g ? extends String
    } 
  }
}
```

## Java Bean and BeanInfo

```java
void method(Class<?> type) {
  BeanInfo beanInfo;
  try {
    beanInfo = Introspector.getBeanInfo(beanType);
  } catch (IntrospectionException e) {
    throw new IllegalStateException(e);
  }
  
  PropertyDescriptor[] properties = beanInfo.getPropertyDescriptors();
  for(PropertyDescriptor property: properties) {
    String name = property.getName();
    Class<?> type = property.getType();
    Method getter = property.getReadMethod();
    Method setter = property.getWriteMethod();
    ...
  }
}
```

```java
void method(String methodName) 
  if (methodName.length() > 3 && methodName.startsWith("get")) {
    String propertyName = Introspector.decapitalize(methodName.substring(3));
    ...
  }
}
```
