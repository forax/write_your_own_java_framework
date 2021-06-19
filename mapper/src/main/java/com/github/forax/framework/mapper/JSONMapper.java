package com.github.forax.framework.mapper;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public final class JSONMapper {
  private static final ClassValue<Generator> GENERATOR_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected Generator computeValue(Class<?> type) {
      var properties = type.isRecord()? recordProperties(type): beanProperties(type);
      var generators = properties
          .<Generator>map(property -> {
            var getter = property.getReadMethod();
            var jsonProperty = getter.getAnnotation(JSONProperty.class);
            var name = jsonProperty == null? property.getName(): jsonProperty.value();
            return (mapper, bean) -> "\"" + name + "\": " + mapper.toJSON(Utils.invoke(bean, getter));
          })
          .toList();
      return (mapper, bean) -> generators.stream()
          .map(generator -> generator.generate(mapper, bean))
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
            throw new IllegalStateException(e);
          }
        });
  }


  private interface Generator {
    String generate(JSONMapper mapper, Object bean);
  }

  private final HashMap<Class<?>, Generator> map = new HashMap<>();

  public <T> void configure(Class<? extends T> type, Function<? super T, String> generator) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(generator);
    var result= map.putIfAbsent(type, (mapper, bean) -> generator.apply(type.cast(bean)));
    if (result != null) {
      throw new IllegalStateException("a generator is already registered for the type " + type.getName());
    }
  }

  public String toJSON(Object o) {
    // TODO use a switch on types when moving to Java 17
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
