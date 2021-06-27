package com.github.forax.framework.mapper;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public final class JSONWriter {
  private interface Generator {
    String generate(JSONWriter writer, Object object);
  }

  private static final ClassValue<Generator> GENERATOR_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected Generator computeValue(Class<?> type) {
      var properties = type.isRecord()? recordProperties(type): beanProperties(type);
      var generators = properties
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

  private static Stream<PropertyDescriptor> beanProperties(Class<?> type) {
    var beanInfo = Utils.beanInfo(type);
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        .filter(property -> !property.getName().equals("class"));
  }

  private static Stream<PropertyDescriptor> recordProperties(Class<?> type) {
    return Arrays.stream(type.getRecordComponents())
        .map(component -> {
          try {
            return new PropertyDescriptor(component.getName(), component.getAccessor(), null);
          } catch (IntrospectionException e) {
            throw new AssertionError(e);
          }
        });
  }

  private final HashMap<Class<?>, Generator> map = new HashMap<>();

  public <T> void configure(Class<? extends T> type, Function<? super T, String> function) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(function);
    var result = map.putIfAbsent(type, (__, object) -> function.apply(type.cast(object)));
    if (result != null) {
      throw new IllegalStateException("already a function registered for type " + type.getName());
    }
  }

  public String toJSON(Object o) {
    /*
    // TODO use a switch on types when moving to Java 17
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
        yield generator.generate(this, o);
      }
    };
    */
    if (o == null) {
      return "null";
    }
    if (o instanceof Boolean value) {
      return "" + value;
    }
    if (o instanceof Number value) {
      return "" + value;
    }
    if (o instanceof String value) {
      return "\"" + value + "\"";
    }
    var type = o.getClass();
    var generator = map.get(type);
    if (generator == null) {
      generator = GENERATOR_CLASS_VALUE.get(type);
    }
    return generator.generate(this, o);
  }
}
