package com.github.forax.framework.mapper;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public class JSONDeserializer {
  private static Constructor<?> findDefaultConstructor(Class<?> beanType) {
    try {
      return beanType.getConstructor();
    } catch (NoSuchMethodException e) {
      throw (NoSuchMethodError) new NoSuchMethodError("no public default constructor").initCause(e);
    }
  }

  private static Type findDeserializerType(Object typeReference) {
    var typeReferenceType = Arrays.stream(typeReference.getClass().getGenericInterfaces())
        .flatMap(superInterface -> superInterface instanceof ParameterizedType parameterizedType? Stream.of(parameterizedType): Stream.empty())
        .filter(superInterface -> superInterface.getRawType() == TypeReference.class)
        .findFirst().orElseThrow(() -> new IllegalArgumentException("invalid TypeReference " + typeReference));
    return typeReferenceType.getActualTypeArguments()[0];
  }


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

  public record Collector<B>(BiFunction<Type, String, Type> qualifier,
                             Supplier<? super B> supplier, Appender<B> appender, Function<B, Object> finisher) {
    public interface Appender<B> {
      void append(B builder, String key, Object value);
    }

    public static Collector<Object> bean(Class<?> clazz) {
      var beanInfo = Utils.beanInfo(clazz);
      var propertyMap = Arrays.stream(beanInfo.getPropertyDescriptors())
          .filter(property -> property.getWriteMethod() != null)
          .collect(toMap(PropertyDescriptor::getName, property -> property));
      var constructor = findDefaultConstructor(clazz);
      return new Collector<>(
          (type, key) -> {
            var property = propertyMap.get(key);
            if (property == null) {
              throw new IllegalStateException("unknown property '" + key + "' for bean " + clazz.getName());
            }
            return property.getPropertyType();
          },
          () -> Utils.newInstance(constructor),
          (bean, key, value) -> {
            var property = propertyMap.get(key);
            if (property == null) {
              throw new IllegalStateException("unknown property '" + key + "' for bean " + clazz.getName());
            }
            Utils.invoke(bean, property.getWriteMethod(), value);
          },
          bean -> bean);
    }

    @SuppressWarnings("unchecked")
    public Collector<Object> erase() {
      return (Collector<Object>) (Collector<?>) this;
    }

    public static Collector<List<Object>> list(Type element) {
      return new Collector<>((_1, _2) -> element, ArrayList::new, (list, key, value) -> list.add(value), List::copyOf);
    }
  }

  public interface TypeMatcher {
    Optional<Collector<Object>> match(Type type);
  }

  private final ArrayList<TypeMatcher> typeMatchers = new ArrayList<>();
  {
    typeMatchers.add(type -> Optional.of(Collector.bean((Class<?>) type)));
  }

  public void addTypeMatcher(TypeMatcher typeMatcher) {
    Objects.requireNonNull(typeMatcher);
    typeMatchers.add(typeMatcher);
  }

  private Collector<Object> findCollector(Type type) {
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
    record Context(Type type, Collector<Object> collector, Object builder) { }
    var visitor = new IncompleteJSONParser.JSONVisitor() {
      private final ArrayDeque<Context> contexts = new ArrayDeque<>();
      private Object result;

      @Override
      public void value(String key, Object value) {
        Context context = contexts.peek();
        assert context != null;
        context.collector.appender.append(context.builder, key, value);
      }

      private void start(String key) {
        var context = contexts.peek();
        var itemType = context == null? type: context.collector.qualifier.apply(context.type, key);
        var collector = findCollector(itemType);
        contexts.push(new Context(itemType, collector, collector.supplier.get()));
      }

      private void end(String key) {
        var context = contexts.pop();
        var result = context.collector.finisher.apply(context.builder);
        if (contexts.isEmpty()) {
          this.result = result;
        } else {
          contexts.peek().collector.appender.append(context.builder, key, result);
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

  @SuppressWarnings("unchecked")
  public <T> T parseJSON(String text, TypeReference<T> typeReference) {
    Objects.requireNonNull(text);
    Objects.requireNonNull(typeReference);
     return (T) parseJSON(text, findDeserializerType(typeReference));
  }
}
