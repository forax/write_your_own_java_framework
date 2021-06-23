package com.github.forax.framework.mapper;

import java.beans.FeatureDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Collectors;
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

  private IncompleteJSONParser.JSONVisitor findVisitor(Type type) {
    // use a switch on type
    if (type instanceof Class<?> clazz) {
      var beanInfo = Utils.beanInfo(clazz);
      var setterMap = Arrays.stream(beanInfo.getPropertyDescriptors())
          .collect(toMap(FeatureDescriptor::getName, PropertyDescriptor::getWriteMethod));
      var bean = Utils.newInstance(findDefaultConstructor(clazz));
      return new IncompleteJSONParser.JSONVisitor() {
        private Method setter;

        @Override
        public void key(String key) {
           setter = setterMap.get(key);
           if (setter == null) {
             throw new IllegalStateException("unknown property " + key);
           }
        }

        @Override
        public void value(Object value) {
          Utils.invoke(bean, setter, value);
          setter = null;
        }

        @Override
        public void startObject() {}

        @Override
        public void endObject() {}

        @Override
        public void startArray() {
          throw new IllegalStateException("unexpected start of an array");
        }

        @Override
        public void endArray() {}
      };
    }
    return null;
  }

  public Object parseJSON(String text, Type type) {
    return null;
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
