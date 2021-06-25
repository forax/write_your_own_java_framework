package com.github.forax.framework.mapper;

import java.beans.PropertyDescriptor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public class JSONDeserializer {
  /*
  private static Type findComponentType(Type type) {
    if (type instanceof ParameterizedType parameterizedType) {
      var rawType = (Class<?>) parameterizedType.getRawType();
      if (rawType == List.class) {
        return parameterizedType.getActualTypeArguments()[0];
      }
    }
    throw new IllegalStateException("can not find component type " + type);
  }*/

  public record Collector<B>(Function<String, Type> qualifier,
                             Supplier<? super B> supplier, Appender<B> appender, Function<B, Object> finisher) {
    public interface Appender<B> {
      void append(B builder, String key, Object value);
    }

    private static PropertyDescriptor findProperty(Map<String, PropertyDescriptor> propertyMap, String key, Class<?> beanClass) {
      var property = propertyMap.get(key);
      if (property == null) {
        throw new IllegalStateException("unknown property '" + key + "' for bean " + beanClass.getName());
      }
      return property;
    }

    public static Collector<Object> bean(Class<?> beanClass) {
      var beanInfo = Utils.beanInfo(beanClass);
      var propertyMap = Arrays.stream(beanInfo.getPropertyDescriptors())
          .filter(property -> property.getWriteMethod() != null)
          .collect(toMap(PropertyDescriptor::getName, property -> property));
      var constructor = Utils.defaultConstructor(beanClass);
      return new Collector<>(
          key -> findProperty(propertyMap, key, beanClass).getPropertyType(),
          () -> Utils.newInstance(constructor),
          (bean, key, value) -> Utils.invokeMethod(bean, findProperty(propertyMap, key, beanClass).getWriteMethod(), value),
          bean -> bean);
    }

    @SuppressWarnings("unchecked")
    private Collector<Object> erase() {
      return (Collector<Object>) (Collector<?>) this;
    }

    private static int findComponentIndex(Map<String, Integer> componentIndexMap, String key, Class<?> recordClass) {
      var index = componentIndexMap.get(key);
      if (index == null) {
        throw new IllegalStateException("unknown component " + key + " for record " + recordClass.getName());
      }
      return index;
    }

    public static Collector<Object[]> record(Class<?> recordClass) {
      var components = recordClass.getRecordComponents();
      var componentIndexMap = IntStream.range(0, components.length)
          .boxed()
          .collect(toMap(i -> components[i].getName(), component -> component));
      var constructor = Utils.canonicalConstructor(recordClass, components);
      return new Collector<>(
          key -> components[findComponentIndex(componentIndexMap, key, recordClass)].getType(),
          () -> new Object[components.length],
          (array, key, value) -> array[findComponentIndex(componentIndexMap, key, recordClass)] = value,
          array -> Utils.newInstance(constructor, array)
      );
    }

    public static Collector<List<Object>> list(Type element) {
      return new Collector<>(__ -> element, ArrayList::new, (list, key, value) -> list.add(value), List::copyOf);
    }
  }

  public interface TypeMatcher {
    Optional<Collector<?>> match(Type type);
  }

  private final ArrayList<TypeMatcher> typeMatchers = new ArrayList<>();
  {
    typeMatchers.add(type -> Optional.of(Collector.bean((Class<?>) type)));
  }

  public void addTypeMatcher(TypeMatcher typeMatcher) {
    Objects.requireNonNull(typeMatcher);
    typeMatchers.add(typeMatcher);
  }

  private Collector<?> findCollector(Type type) {
    /*
    for(var typeMatcher: Utils.reverseList(typeMatchers)) {
      var collectorOpt = typeMatcher.match(type);
      if (collectorOpt.isPresent()) {
        return collectorOpt.orElseThrow();
      }
    }
    throw new IllegalStateException("no type match matches " + type.getTypeName());
     */
    return Utils.reverseList(typeMatchers).stream()
        .flatMap(typeMatcher -> typeMatcher.match(type).stream())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no type match matches " + type.getTypeName()));
  }

  public Object parseJSON(String text, Type type) {
    Objects.requireNonNull(text);
    Objects.requireNonNull(type);
    record Context(Collector<Object> collector, Object data) { }
    var visitor = new IncompleteJSONParser.JSONVisitor() {
      private final ArrayDeque<Context> contexts = new ArrayDeque<>();
      private Object result;

      @Override
      public void value(String key, Object value) {
        Context context = contexts.peek();
        assert context != null;
        context.collector.appender.append(context.data, key, value);
      }

      private void start(String key) {
        var context = contexts.peek();
        var itemType = context == null? type: context.collector.qualifier.apply(key);
        var collector = findCollector(itemType).erase();
        contexts.push(new Context(collector, collector.supplier.get()));
      }

      private void end(String key) {
        var context = contexts.pop();
        var result = context.collector.finisher.apply(context.data);
        if (contexts.isEmpty()) {
          this.result = result;
        } else {
          contexts.peek().collector.appender.append(context.data, key, result);
        }
      }

      @Override
      public void startObject(String key) {
        start(key);
      }
      @Override
      public void endObject(String key) {
        end(key);
      }
      @Override
      public void startArray(String key) {
        start(key);
      }
      @Override
      public void endArray(String key) {
        end(key);
      }
    };
    IncompleteJSONParser.parse(text, visitor);
    return visitor.result;
  }

  public <T> T parseJSON(String text, Class<T> type) {
    Objects.requireNonNull(text);
    Objects.requireNonNull(type);
    return type.cast(parseJSON(text, (Type) type));
  }

  public interface TypeReference<T> {}

  private static Type findDeserializerType(Object typeReference) {
    var typeReferenceType = Arrays.stream(typeReference.getClass().getGenericInterfaces())
        .flatMap(superInterface -> superInterface instanceof ParameterizedType parameterizedType? Stream.of(parameterizedType): Stream.empty())
        .filter(superInterface -> superInterface.getRawType() == TypeReference.class)
        .findFirst().orElseThrow(() -> new IllegalArgumentException("invalid TypeReference " + typeReference));
    return typeReferenceType.getActualTypeArguments()[0];
  }

  @SuppressWarnings("unchecked")
  public <T> T parseJSON(String text, TypeReference<T> typeReference) {
    Objects.requireNonNull(text);
    Objects.requireNonNull(typeReference);
     return (T) parseJSON(text, findDeserializerType(typeReference));
  }
}
