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
  public record Collector<B>(Function<? super String, ? extends Type> qualifier,
                             Supplier<? extends B> supplier, Populater<B> populater, Function<? super B, ?> finisher) {
    public interface Populater<B> {
      void populate(B builder, String key, Object value);
    }

    @SuppressWarnings("unchecked")
    private Collector<Object> raw() {
      return (Collector<Object>) (Collector<?>) this;
    }

    public Collector {
      Objects.requireNonNull(qualifier);
      Objects.requireNonNull(supplier);
      Objects.requireNonNull(populater);
      Objects.requireNonNull(finisher);
    }

    private static PropertyDescriptor findProperty(Map<String, PropertyDescriptor> propertyMap, String key, Class<?> beanClass) {
      var property = propertyMap.get(key);
      if (property == null) {
        throw new IllegalStateException("unknown property '" + key + "' for bean " + beanClass.getName());
      }
      return property;
    }

    public static Collector<Object> bean(Class<?> beanClass) {
      Objects.requireNonNull(beanClass);
      var beanInfo = Utils.beanInfo(beanClass);
      var propertyMap = Arrays.stream(beanInfo.getPropertyDescriptors())
          .collect(toMap(PropertyDescriptor::getName, property -> property));
      var constructor = Utils.defaultConstructor(beanClass);
      return new Collector<>(
          key -> findProperty(propertyMap, key, beanClass).getWriteMethod().getGenericParameterTypes()[0],
          () -> Utils.newInstance(constructor),
          (bean, key, value) -> Utils.invokeMethod(bean, findProperty(propertyMap, key, beanClass).getWriteMethod(), value),
          bean -> bean
      );
    }

    public static Collector<List<Object>> list(Type element) {
      Objects.requireNonNull(element);
      return new Collector<>(__ -> element, ArrayList::new, (list, key, value) -> list.add(value), List::copyOf);
    }

    private static int findComponentIndex(Map<String, Integer> componentIndexMap, String key, Class<?> recordClass) {
      var index = componentIndexMap.get(key);
      if (index == null) {
        throw new IllegalStateException("unknown component " + key + " for record " + recordClass.getName());
      }
      return index;
    }

    public static Collector<Object[]> record(Class<?> recordClass) {
      Objects.requireNonNull(recordClass);
      var components = recordClass.getRecordComponents();
      var componentIndexMap = IntStream.range(0, components.length)
          .boxed()
          .collect(toMap(i -> components[i].getName(), component -> component));
      var constructor = Utils.canonicalConstructor(recordClass, components);
      return new Collector<>(
          key -> components[findComponentIndex(componentIndexMap, key, recordClass)].getGenericType(),
          () -> new Object[components.length],
          (array, key, value) -> array[findComponentIndex(componentIndexMap, key, recordClass)] = value,
          array -> Utils.newInstance(constructor, array)
      );
    }
  }

  @FunctionalInterface
  public interface TypeMatcher {
    Optional<Collector<?>> match(Type type);
  }

  private final ArrayList<TypeMatcher> typeMatchers = new ArrayList<>();

  public void addTypeMatcher(TypeMatcher typeMatcher) {
    Objects.requireNonNull(typeMatcher);
    typeMatchers.add(typeMatcher);
  }

  private Collector<?> findCollector(Type type) {
    return Utils.reverseList(typeMatchers).stream()
        .flatMap(typeMatcher -> typeMatcher.match(type).stream())
        .findFirst()
        .orElseGet(() -> Collector.bean(Utils.erase(type)));
  }


  // Q4
  public <T> T parseJSON(String text, Class<T> beanClass) {
    return beanClass.cast(parseJSON(text, (Type) beanClass));
  }

  public Object parseJSON(String text, Type type) {
    Objects.requireNonNull(text);
    Objects.requireNonNull(type);
    var visitor = new ToyJSONParser.JSONVisitor() {
      record Context(Collector<Object> collector, Object data) {}

      private final ArrayDeque<Context> contexts = new ArrayDeque<>();
      private Object result;


      @Override
      public void value(String key, Object value) {
        var context = contexts.peek();
        assert context != null;
        context.collector.populater.populate(context.data, key, value);
      }

      private void start(String key) {
        var context = contexts.peek();
        var itemType = context == null? type: context.collector.qualifier.apply(key);
        var collector = findCollector(itemType).raw();
        var data = collector.supplier.get();
        contexts.push(new Context(collector, data));
      }

      private void end(String key) {
        var context = contexts.pop();
        var result = context.collector.finisher.apply(context.data);
        if (contexts.isEmpty()) {
          this.result = result;
        } else {
          var enclosingContext = contexts.peek();
          enclosingContext.collector.populater.populate(enclosingContext.data, key, result);
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
    ToyJSONParser.parse(text, visitor);
    return visitor.result;
  }

  public interface TypeReference<T> {}

  private static Type findDeserializerType(Object typeReference) {
    var typeReferenceType = Arrays.stream(typeReference.getClass().getGenericInterfaces())
        .flatMap(t -> t instanceof ParameterizedType parameterizedType? Stream.of(parameterizedType): Stream.empty())
        .filter(t -> t.getRawType() == TypeReference.class)
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
