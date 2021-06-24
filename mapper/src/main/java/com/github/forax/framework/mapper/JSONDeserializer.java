package com.github.forax.framework.mapper;

import java.beans.FeatureDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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

  private interface Appender<B> {
    void append(B builder, String key, Object value);
  }

  private record Collector<B>(Supplier<B> supplier, Appender<B> appender, Function<B, Object> finisher) {
    static Collector<Object> bean(Class<?> clazz) {
      var beanInfo = Utils.beanInfo(clazz);
      var setterMap = Arrays.stream(beanInfo.getPropertyDescriptors())
          .collect(toMap(FeatureDescriptor::getName, PropertyDescriptor::getWriteMethod));
      var constructor = findDefaultConstructor(clazz);
      return new Collector<>(
          () -> Utils.newInstance(constructor),
          (builder, key, value) -> Utils.invoke(builder, setterMap.get(key), value),
          b -> b);
    }
    static Collector<List<Object>> list() {
      return new Collector<>(ArrayList::new, (list, key, value) -> list.add(value), List::copyOf);
    }
  }

  /*
  private interface TypeWalker {
    Class<?> rawContainer();
    Class<?> rawValue();
    Type container();
    Type value();

    TypeWalker key(String key);
    TypeWalker element();
  }
  */

  private static Type findQualifiedType(String key, Type type) {
    var clazz = (Class<?>) type;
    var beanInfo = Utils.beanInfo(clazz);
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        .filter(property -> property.getName().equals(key))
        .map(PropertyDescriptor::getPropertyType)
        .findFirst().orElseThrow();
  }

  private static Type findComponentType(Type type) {
    if (type instanceof ParameterizedType parameterizedType) {
      var rawType = (Class<?>) parameterizedType.getRawType();
      if (rawType == List.class) {
        return parameterizedType.getActualTypeArguments()[0];
      }
    }
    throw new IllegalStateException("can not find component type " + type);
  }

  public Object parseJSON(String text, Type type) {
    record Context(String key, Type type, Collector<Object> collector, Object builder) { }
    var visitor = new IncompleteJSONParser.JSONVisitor() {
      private final ArrayDeque<Context> contexts = new ArrayDeque<>();
      private Object result;

      @Override
      public void value(String key, Object value) {
        Context context = contexts.peek();
        context.collector.appender.append(context.builder, key, value);
      }

      @Override
      public void startObject(String key) {
        var context = contexts.peek();
        var itemType = findQualifiedType(key, context == null? type: context.type);
        var factory = Collector.bean((Class<?>) type);
        contexts.push(new Context(key, itemType, factory, factory.supplier.get()));
      }

      @Override
      public void endObject() {
        var context = contexts.pop();
        var finished = context.collector.finisher.apply(context.builder);
        if (contexts.isEmpty()) {
          this.result = finished;
        } else {
          contexts.peek().collector.appender.append(context.builder, context.key, finished);
        }
      }

      @Override
      public void startArray(String key) {
        var context = contexts.peek();
        var itemType = findComponentType(findQualifiedType(key, context == null? type: context.type));
        @SuppressWarnings("unchecked")
        var factory = (Collector<Object>) (Collector<?>) Collector.list();
        contexts.push(new Context(key, itemType, factory, factory.supplier.get()));
      }

      @Override
      public void endArray() {
        var context = contexts.pop();
        var finished = context.collector.finisher.apply(context.builder);
        if (contexts.isEmpty()) {
          this.result = finished;
        } else {
          contexts.peek().collector.appender.append(context.builder, context.key, finished);
        }
      }
    };
    IncompleteJSONParser.parse(text, visitor);
    return visitor.result;
  }

  public <T> T parseJSON(String text, Class<T> type) {
    return type.cast(parseJSON(text, (Type) type));
  }

  public interface TypeReference<T> {}


  @SuppressWarnings("unchecked")
  public <T> T parseJSON(String text, TypeReference<T> typeReference) {
     return (T) parseJSON(text, findDeserializerType(typeReference));
  }
}
