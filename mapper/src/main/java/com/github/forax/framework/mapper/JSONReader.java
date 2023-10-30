package com.github.forax.framework.mapper;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class JSONReader {
  private record BeanData(Constructor<?> constructor, Map<String, PropertyDescriptor> propertyMap) {
    PropertyDescriptor findProperty(String key) {
      var property = propertyMap.get(key);
      if (property == null) {
        throw new IllegalStateException("unknown key " + key + " for bean " + constructor.getDeclaringClass().getName());
      }
      return property;
    }
  }

  private static final ClassValue<BeanData> BEAN_DATA_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected BeanData computeValue(Class<?> type) {
      var beanInfo = Utils.beanInfo(type);
      var constructor = Utils.defaultConstructor(type);
      var map = Arrays.stream(beanInfo.getPropertyDescriptors())
          .filter(property -> !property.getName().equals("class"))
          .collect(Collectors.toMap(PropertyDescriptor::getName, Function.identity()));
      return new BeanData(constructor, map);
    }
  };

  public record ObjectBuilder<T>(Function<? super String, ? extends Type> typeProvider,
                                 Supplier<? extends T> supplier,
                                 Populater<? super T> populater,
                                 Function<? super T, ?> finisher) {
    public interface Populater<T> {
      void populate(T instance, String key, Object value);
    }

    public ObjectBuilder {
      Objects.requireNonNull(typeProvider);
      Objects.requireNonNull(supplier);
      Objects.requireNonNull(populater);
      Objects.requireNonNull(finisher);
    }

    public static ObjectBuilder<Object> bean(Class<?> beanClass) {
      Objects.requireNonNull(beanClass);
      var beanData = BEAN_DATA_CLASS_VALUE.get(beanClass);
      return new ObjectBuilder<>(
          key -> beanData.findProperty(key).getWriteMethod().getGenericParameterTypes()[0],
          () -> Utils.newInstance(beanData.constructor),
          (instance, key, value) -> {
            var property = beanData.findProperty(key);
            Utils.invokeMethod(instance, property.getWriteMethod(), value);
          },
          Function.identity()
      );
    }

    public static ObjectBuilder<List<Object>> list(Type elementType) {
      Objects.requireNonNull(elementType);
      return new ObjectBuilder<>(
          key -> elementType,
          ArrayList::new,
          (list, key, value) -> list.add(value),
          List::copyOf
      );
    }

    public static ObjectBuilder<Object[]> record(Class<?> recordClass) {
      Objects.requireNonNull(recordClass);
      var components = recordClass.getRecordComponents();
      var map = IntStream.range(0, components.length)
          .boxed()
          .collect(Collectors.toMap(i -> components[i].getName(), Function.identity()));
      var constructor = Utils.canonicalConstructor(recordClass, components);
      return new ObjectBuilder<>(
          key -> components[map.get(key)].getGenericType(),
          () -> new Object[components.length],
          (array, key, value) -> array[map.get(key)] = value,
          array -> Utils.newInstance(constructor, array)
      );
    }
  }

  @FunctionalInterface
  public interface TypeMatcher {
    Optional<ObjectBuilder<?>> match(Type type);
  }

  private final ArrayList<TypeMatcher> typeMatchers = new ArrayList<>();

  public void addTypeMatcher(TypeMatcher typeMatcher) {
    Objects.requireNonNull(typeMatcher);
    typeMatchers.add(typeMatcher);
  }

  ObjectBuilder<?> findObjectBuilder(Type type) {
    return typeMatchers.reversed().stream()
        .flatMap(typeMatcher -> typeMatcher.match(type).stream())
        .findFirst()
        .orElseGet(() -> ObjectBuilder.bean(Utils.erase(type)));
  }

  private record Context<T>(ObjectBuilder<T> objectBuilder, T result) {
    private static <T> Context<T> create(ObjectBuilder<T> objectBuilder) {
      return new Context<>(objectBuilder, objectBuilder.supplier.get());
    }

    private void populate(String key, Object value) {
      objectBuilder.populater.populate(result, key, value);
    }

    private Object finish() {
      return objectBuilder.finisher.apply(result);
    }
  }

  public <T> T parseJSON(String text, Class<T> expectedClass) {
    return expectedClass.cast(parseJSON(text, (Type) expectedClass));
  }

  public Object parseJSON(String text, Type expectedType) {
    Objects.requireNonNull(text);
    Objects.requireNonNull(expectedType);
    var stack = new ArrayDeque<Context<?>>();
    var visitor = new ToyJSONParser.JSONVisitor() {
      private Object result;

      @Override
      public void value(String key, Object value) {
        var context = stack.peek();
        context.populate(key, value);
      }

      @Override
      public void startObject(String key) {
        var context = stack.peek();
        var type = context == null ? expectedType : context.objectBuilder.typeProvider.apply(key);
        var objectbuilder = findObjectBuilder(type);
        stack.push(Context.create(objectbuilder));
      }

      @Override
      public void endObject(String key) {
        var instance = stack.pop().finish();
        if (stack.isEmpty()) {
          result = instance;
          return;
        }
        var context = stack.peek();
        context.populate(key, instance);
      }

      @Override
      public void startArray(String key) {
        startObject(key);
      }

      @Override
      public void endArray(String key) {
        endObject(key);
      }
    };
    ToyJSONParser.parse(text, visitor);
    return visitor.result;
  }

  public interface TypeReference<T> { }

  private static Type findElemntType(TypeReference<?> typeReference) {
    var typeReferenceType = Arrays.stream(typeReference.getClass().getGenericInterfaces())
        .flatMap(t -> t instanceof ParameterizedType parameterizedType? Stream.of(parameterizedType): null)
        .filter(t -> t.getRawType() == TypeReference.class)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("invalid TypeReference " + typeReference));
    return typeReferenceType.getActualTypeArguments()[0];
  }

  public <T> T parseJSON(String text, TypeReference<T> typeReference) {
    var elementType = findElemntType(typeReference);
    @SuppressWarnings("unchecked")
    var result = (T) parseJSON(text, elementType);
    return result;
  }
}
