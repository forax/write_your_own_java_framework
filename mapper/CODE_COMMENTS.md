# Code comments

### Q1

```java
  public String toJSON(Object o) {
    return switch (o) {
        case null -> "null";
        case Boolean value -> "" + value;
        case Number value -> "" + value;
        case String value -> "\"" + value + "\"";
        default -> throw new IllegalArgumentException("not supported yet");
    };
  }
```

### Q2

```java
  public String toJSON(Object o) {
    return switch (o) {
        case null -> "null";
        case Boolean value -> "" + value;
        case Number value -> "" + value;
        case String value -> "\"" + value + "\"";
        default -> {
          var beanInfo = Utils.beanInfo(o.getClass());
          return Arrays.stream(beanInfo.getPropertyDescriptors())
              .filter(property -> !property.getName().equals("class"))
              .map(property -> {
                var name = property.getName();
                var getter = property.getReadMethod();
                var value = Utils.invoke(o, getter);
                return "\"" + name + "\": " + toJSON(value);
              })
              .collect(Collectors.joining(", ", "{", "}"));
        }
    };
    
  }
```

### Q3

```java
  private interface Generator {
    String generate(JSONWriter writer, Object object);
  }

  private static final ClassValue<PropertyDescriptor[]> PROPERTIES_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected PropertyDescriptor[] computeValue(Class<?> type) {
      var beanInfo = Utils.beanInfo(type);
      var list = Arrays.stream(beanInfo.getPropertyDescriptors())
          .filter(property -> !property.getName().equals("class"))
          .toArray(PropertyDescriptor[]::new);
    }
  };

  public String toJSON(Object o) {
    return switch (o) {
      case null -> "null";
      case Boolean value -> "" + value;
      case Number value -> "" + value;
      case String value -> "\"" + value + "\"";
      default -> {
        var properties = PROPERTIES_CLASS_VALUE.get(o.getClass());
        return Arrays.stream(properties
            .map(property -> {
               var name = property.getName();
               var getter = property.getReadMethod();
               var value = Utils.invoke(o, getter);
               return "\"" + name + "\": " + writer.toJSON(value);
            })
            .collect(joining(", ", "{", "}"));
  }
```

### Q4

```java
  private interface Generator {
    String generate(JSONWriter writer, Object object);
  }

  private static final ClassValue<Generator> GENERATOR_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected Generator computeValue(Class<?> type) {
      var beanInfo = Utils.beanInfo(type);
      var list = Arrays.stream(beanInfo.getPropertyDescriptors())
          .filter(property -> !property.getName().equals("class"))
          .<Generator>map(property -> {
            var key = "\"" + property.getName() + "\": ";
            var getter = property.getReadMethod();
            return (writer, o) -> key + writer.toJSON(Utils.invoke(o, getter));
          })
          .toList();
      return (writer, object) -> list.stream()
          .map(generator -> generator.generate(writer, object))
          .collect(joining(", ", "{", "}"));
    }
  };

  public String toJSON(Object o) {
    return switch (o) {
        case null -> "null";
        case Boolean value -> "" + value;
        case Number value -> "" + value;
        case String value -> "\"" + value + "\"";
        default -> {
          var generator = GENERATOR_CLASS_VALUE.get(o.getClass());
          return generator.generate(this, o);
        }
    };
  }
```

### Q5

```java
  private interface Generator {
    String generate(JSONWriter writer, Object object);
  }
  
  private final HashMap<Class<?>, Generator> map = new HashMap<>();

  public <T> void configure(Class<? extends T> type, Function<? super T, String> function) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(function);
    var result = map.putIfAbsent(type, (writer, object) -> function.apply(type.cast(object)));
    if (result != null) {
      throw new IllegalStateException("already a function registered for type " + type.getName());
    }
  }

  public String toJSON(Object o) {
    return switch (o) {
        case null -> "null";
        case Boolean value -> "" + value;
        case Number value -> "" + value;
        case String value -> "\"" + value + "\"";
        default -> {
          var type = o.getClass();
          var generator = map.get(type);
          if (generator == null) {
            generator = GENERATOR_CLASS_VALUE.get(type);
          }
          return generator.generate(this, o);
        }
    };
  }
```

### Q6

```java
  private static final ClassValue<Generator> GENERATOR_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected Generator computeValue(Class<?> type) {
      var beanInfo = Utils.beanInfo(type);
      var list = Arrays.stream(beanInfo.getPropertyDescriptors())
          .filter(property -> !property.getName().equals("class"))
          .<Generator>map(property -> {
            var getter = property.getReadMethod();
            var propertyAnnotation = getter.getAnnotation(JSONProperty.class);
            var propertyName = propertyAnnotation == null? property.getName(): propertyAnnotation.value();
            var key = "\"" + propertyName + "\": ";
            return (writer, o) -> key + writer.toJSON(Utils.invoke(o, getter));
          })
          .toList();
      return (writer, object) -> list.stream()
          .map(generator -> generator.generate(writer, object))
          .collect(joining(", ", "{", "}"));
    }
  };
```

### Q7

```java
  private static final ClassValue<Generator> GENERATOR_CLASS_VALUE = new ClassValue<>() {
      @Override
      protected Generator computeValue(Class<?> type) {
        var properties = type.isRecord()? recordProperties(type): beanProperties(type);
        var generators = properties.stream()
            .<Generator>map(property -> {
              var getter = property.getReadMethod();
              var propertyAnnotation = getter.getAnnotation(JSONProperty.class);
              var propertyName = propertyAnnotation == null? property.getName(): propertyAnnotation.value();
              var key = "\"" + propertyName + "\": ";
              return (writer, o) -> key + writer.toJSON(Utils.invokeMethod(o, getter));
           })
           .toList();
        return (writer, object) -> generators.stream()
            .map(generator -> generator.generate(writer, object))
            .collect(joining(", ", "{", "}"));
      }
  };

  private static List<PropertyDescriptor> beanProperties(Class<?> type) {
    var beanInfo = Utils.beanInfo(type);
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        .filter(property -> !property.getName().equals("class"))
        .toList();
  }

  private static List<PropertyDescriptor> recordProperties(Class<?> type) {
    return Arrays.stream(type.getRecordComponents())
        .map(component -> {
          try {
            return new PropertyDescriptor(component.getName(), component.getAccessor(), null);
          } catch (IntrospectionException e) {
            throw new AssertionError(e);
          }
        })
        .toList();
  }
```
