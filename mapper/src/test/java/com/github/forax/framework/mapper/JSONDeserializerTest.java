package com.github.forax.framework.mapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JSONDeserializerTest {

  @Nested
  public class Q1 {

    @SuppressWarnings("unused")
    public static class SimpleBean {
      private String name;
      private int age;

      public void setName(String name) {
        this.name = name;
      }
      public void setAge(int age) {
        this.age = age;
      }
    }

    @Test @Tag("Q1")
    public void parseJSON() {
      var deserializer = new JSONDeserializer();
      SimpleBean bean = deserializer.parseJSON("""
        {
          "name": "Bob",
          "age": 23
        }
        """, SimpleBean.class);
      assertAll(
          () -> assertEquals("Bob", bean.name),
          () -> assertEquals(23, bean.age)
      );
    }

    @Test @Tag("Q1")
    public void parseJSONPartial() {
      var deserializer = new JSONDeserializer();
      var bean = deserializer.parseJSON("""
        {
          "name": "Bob"
        }
        """, SimpleBean.class);
      assertEquals("Bob", bean.name);
    }

    @Test @Tag("Q1")
    public void parseJSONEmpty() {
      var deserializer = new JSONDeserializer();
      var bean = deserializer.parseJSON("""
        {}
        """, SimpleBean.class);
      assertNull(bean.name);
    }

    @SuppressWarnings("unused")
    public static class PrimitiveBean {
      private Void key1;
      private boolean key2;
      private boolean key3;
      private int key4;
      private double key5;
      private String key6;

      private final HashSet<String> keys = new HashSet<>();

      public void setKey1(Void key1) {
        this.key1 = key1;
        keys.add("key1");
      }
      public void setKey2(boolean key2) {
        this.key2 = key2;
        keys.add("key2");
      }
      public void setKey3(boolean key3) {
        this.key3 = key3;
        keys.add("key3");
      }
      public void setKey4(int key4) {
        this.key4 = key4;
        keys.add("key4");
      }
      public void setKey5(double key5) {
        this.key5 = key5;
        keys.add("key5");
      }
      public void setKey6(String key6) {
        this.key6 = key6;
        keys.add("key6");
      }
    }

    @Test @Tag("Q1")
    public void parseJSONPrimitive() {
      var deserializer = new JSONDeserializer();
      var bean = deserializer.parseJSON("""
        {
          "key1": null,
          "key2": false,
          "key3": true,
          "key4": 12,
          "key5": 144.4,
          "key6": "string"
        }
        """, PrimitiveBean.class);
      assertAll(
          () -> assertEquals(Set.of("key1", "key2", "key3", "key4", "key5", "key6"), bean.keys),
          () -> assertNull(bean.key1),
          () -> assertFalse(bean.key2),
          () -> assertTrue(bean.key3),
          () -> assertEquals(12, bean.key4),
          () -> assertEquals(144.4, bean.key5),
          () -> assertEquals("string", bean.key6)
      );
    }

    @Test @Tag("Q1")
    public void parseJSONInvalidKey() {
      var deserializer = new JSONDeserializer();
      var exception = assertThrows(IllegalStateException.class, () -> {
        deserializer.parseJSON("""
        {
          "invalidKey": "oops"
        }
        """, SimpleBean.class);
      });
      assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test @Tag("Q1")
    public void parseJSONClassPrecondition() {
      var deserializer = new JSONDeserializer();
      assertAll(
          () -> assertThrows(NullPointerException.class, () -> deserializer.parseJSON(null, String.class)),
          () -> assertThrows(NullPointerException.class, () -> deserializer.parseJSON("", (Class<?>) null))
      );
    }

  }  // end of Q1


  @Nested
  public class Q2 {

    @SuppressWarnings("unused")
    public static class Person {
      private Address address;

      public void setAddress(Address address) {
        this.address = address;
      }
    }
    @SuppressWarnings("unused")
    public static class Address {
      private String zipCode;

      public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
      }
    }

    @Test @Tag("Q2")
    public void parseJSONRecursive() {
      var deserializer = new JSONDeserializer();
      var person = deserializer.parseJSON("""
        {
          "address": {
            "zipCode": "75001"
          }
        }
        """, Person.class);
      assertEquals("75001", person.address.zipCode);
    }

  }  // end of Q2


  @Nested
  public class Q3 {

    @SuppressWarnings("unused")
    public static class Person {
      private String name;
      private int age;

      public void setName(String name) {
        this.name = name;
      }
      public void setAge(int age) {
        this.age = age;
      }
    }

    @Test @Tag("Q3")
    public void collectorBean() {
      var collector = JSONDeserializer.Collector.bean(Person.class);
      var bean = collector.supplier().get();
      collector.populater().populate(bean, "name", "Bob");
      collector.populater().populate(bean, "age", 29);
      var person = (Person) collector.finisher().apply(bean);

      assertAll(
          () -> assertEquals("Bob", person.name),
          () -> assertEquals(29, person.age)
      );
    }

    @Test @Tag("Q3")
    public void collectorBeanQualifierType() {
      var collector = JSONDeserializer.Collector.bean(Person.class);
      assertAll(
          () -> assertEquals(String.class, collector.qualifier().apply("name")),
          () -> assertEquals(int.class, collector.qualifier().apply("age"))
      );
    }

    @Test @Tag("Q3")
    public void collectorBeanPreconditions() {
      assertThrows(NullPointerException.class, () -> JSONDeserializer.Collector.bean(null));
    }

  }  // end of Q3


  @Nested class Q4 {
    @Test @Tag("Q4")
    public void parseJSONTypePrecondition() {
      var deserializer = new JSONDeserializer();
      assertAll(
          () -> assertThrows(NullPointerException.class, () -> deserializer.parseJSON(null, (Type) String.class)),
          () -> assertThrows(NullPointerException.class, () -> deserializer.parseJSON("", (Type) null))
      );
    }

    @SuppressWarnings("unused")
    public static class PreciseTypeBean {
      public void setFoo(List<String> list) {}
    }

    @Test @Tag("Q4")
    public void collectorBeanQualifierPreciseType() {
      var collector = JSONDeserializer.Collector.bean(PreciseTypeBean.class);
      var type = collector.qualifier().apply("foo");
      assertAll(
          () -> assertNotEquals(List.class, type),
          () -> assertEquals(List.class, ((ParameterizedType) type).getRawType()),
          () -> assertEquals(String.class, ((ParameterizedType) type).getActualTypeArguments()[0])
      );
    }

  }  // end of Q4


  @Nested
  public class Q5 {
    @Test @Tag("Q5")
    public void collectorListOfStrings() {
      var collector = JSONDeserializer.Collector.list(String.class);
      var list = collector.supplier().get();
      collector.populater().populate(list, null, "Bob");
      collector.populater().populate(list, null, "Ana");
      @SuppressWarnings("unchecked")
      var unmodifiableList = (List<String>) (List<?>) collector.finisher().apply(list);

      assertAll(
          () -> assertEquals(List.of("Bob", "Ana"), list),
          () -> assertEquals(List.of("Bob", "Ana"), unmodifiableList),
          () -> assertThrows(UnsupportedOperationException.class, () -> unmodifiableList.add("foo"))
      );
    }

    @Test @Tag("Q5")
    public void collectorListOfIntegers() {
      var collector = JSONDeserializer.Collector.list(Integer.class);
      var list = collector.supplier().get();
      collector.populater().populate(list, null, 42);
      collector.populater().populate(list, null, 856);
      @SuppressWarnings("unchecked")
      var unmodifiableList = (List<Integer>) (List<?>) collector.finisher().apply(list);

      assertAll(
          () -> assertEquals(List.of(42, 856), list),
          () -> assertEquals(List.of(42, 856), unmodifiableList),
          () -> assertThrows(UnsupportedOperationException.class, () -> unmodifiableList.add(17))
      );
    }

    @Test @Tag("Q5")
    public void collectorListQualifierType() {
      var collector = JSONDeserializer.Collector.list(Integer.class);
      assertEquals(Integer.class, collector.qualifier().apply(null));
    }

    @Test @Tag("Q5")
    public void collectorListPreconditions() {
      assertThrows(NullPointerException.class, () -> JSONDeserializer.Collector.list(null));
    }

    public static class IntArrayBean {
      private List<Integer> values;

      public void setValues(List<Integer> values) {
        this.values = values;
      }
    }

    private static JSONDeserializer.TypeMatcher listTypeMatcher() {
      return type -> Optional.of(type)
          .flatMap(t -> t instanceof ParameterizedType parameterizedType? Optional.of(parameterizedType): Optional.empty())
          .filter(t -> t.getRawType() == List.class)
          .map(t -> JSONDeserializer.Collector.list(t.getActualTypeArguments()[0]));
    }

    @Test @Tag("Q5")
    public void parseJSONWithAList() throws NoSuchMethodException {
      var listOfIntegers = IntArrayBean.class.getMethod("setValues", List.class).getGenericParameterTypes()[0];
      var deserializer = new JSONDeserializer();
      deserializer.addTypeMatcher(listTypeMatcher());
      @SuppressWarnings("unchecked")
      var list = (List<Integer>) deserializer.parseJSON("""
        [
          1, 5, 78, 4
        ]
        """, listOfIntegers);
      assertEquals(List.of(1, 5, 78, 4), list);
    }

    @Test @Tag("Q5")
    public void parseJSONWithABeanAndAList() {
      var deserializer = new JSONDeserializer();
      deserializer.addTypeMatcher(listTypeMatcher());
      var bean = deserializer.parseJSON("""
        {
          "values": [ 12, "foo", 45.2 ]
        }
        """, IntArrayBean.class);
      assertEquals(List.of(12, "foo", 45.2), bean.values);
    }

    @Test @Tag("Q5")
    public void parseJSONWithAUserDefinedCollector() {
      var deserializer = new JSONDeserializer();
      deserializer.addTypeMatcher(type -> Optional.of(new JSONDeserializer.Collector<>(
          key -> String.class,
          () -> new StringJoiner(", ", "{", "}"),
          (joiner, key, value) -> joiner.add(key + "=" + value.toString()),
          StringJoiner::toString
      )));
      var string = deserializer.parseJSON("""
        {
          "foo": 3,
          "bar": "hello"
        }
        """, String.class);
      assertEquals("{foo=3, bar=hello}", string);
    }

    @SuppressWarnings("unused")
    public static final class Car {
      private String owner;
      private String color;

      public Car() {}

      public Car(String owner, String color) {
        this.owner = owner;
        this.color = color;
      }

      public void setOwner(String owner) {
        this.owner = owner;
      }
      public void setColor(String color) {
        this.color = color;
      }

      @Override
      public boolean equals(Object o) {
        return o instanceof Car car && owner.equals(car.owner) && color.equals(car.color);
      }

      @Override
      public int hashCode() {
        return Objects.hash(owner, color);
      }
    }

    @Test @Tag("Q5")
    public void parseJSONListOfCar() throws NoSuchFieldException {
      var listOfCar = new Object() {
        List<Car> exemplar;
      }.getClass().getDeclaredField("exemplar").getGenericType();

      var deserializer = new JSONDeserializer();
      deserializer.addTypeMatcher(listTypeMatcher());
      var string = deserializer.parseJSON("""
        [
          { "owner": "Bob", "color": "red" },
          { "owner": "Ana", "color": "black" }
        ]
        """, listOfCar);
      assertEquals(List.of(new Car("Bob", "red"), new Car("Ana", "black")), string);
    }

    @Test @Tag("Q5")
    public void addTypeMatcherPreconditions() {
      var deserializer = new JSONDeserializer();
      assertThrows(NullPointerException.class, () -> deserializer.addTypeMatcher(null));
    }

  }  // end of Q5


  @Nested
  public class Q6 {

    private static JSONDeserializer.TypeMatcher listTypeMatcher() {
      return type -> Optional.of(type)
          .flatMap(t -> t instanceof ParameterizedType parameterizedType? Optional.of(parameterizedType): Optional.empty())
          .filter(t -> t.getRawType() == List.class)
          .map(t -> JSONDeserializer.Collector.list(t.getActualTypeArguments()[0]));
    }

    @Test @Tag("Q6")
    public void parseJSONTypeReference() {
      var deserializer = new JSONDeserializer();
      deserializer.addTypeMatcher(listTypeMatcher());
      @SuppressWarnings("unchecked")
      var list = (List<Integer>) deserializer.parseJSON("""
          [
            1, 5, 78, 4
          ]
          """, new JSONDeserializer.TypeReference<List<Integer>>() {});
      assertEquals(List.of(1, 5, 78, 4), list);
    }

    @Test @Tag("Q6")
    public void parseJSONTypeReferencePrecondition() {
      var deserializer = new JSONDeserializer();
      assertAll(
          () -> assertThrows(NullPointerException.class, () -> deserializer.parseJSON(null, new JSONDeserializer.TypeReference<String>() {})),
          () -> assertThrows(NullPointerException.class, () -> deserializer.parseJSON("", (JSONDeserializer.TypeReference<?>) null))
      );
    }

  }  // end of Q6


  @Nested
  public class Q7 {

    private static JSONDeserializer.TypeMatcher listTypeMatcher() {
      return type -> Optional.of(type)
          .flatMap(t -> t instanceof ParameterizedType parameterizedType? Optional.of(parameterizedType): Optional.empty())
          .filter(t -> t.getRawType() == List.class)
          .map(t -> JSONDeserializer.Collector.list(t.getActualTypeArguments()[0]));
    }

    public record Person(String name, int age) { }

    @Test @Tag("Q7")
    public void collectorRecord() {
      var collector = JSONDeserializer.Collector.record(Person.class);
      var array = collector.supplier().get();
      collector.populater().populate(array, "name", "Bob");
      collector.populater().populate(array, "age", 29);
      var person = (Person) collector.finisher().apply(array);

      assertAll(
          () -> assertEquals("Bob", person.name),
          () -> assertEquals(29, person.age)
      );
    }

    @Test @Tag("Q7")
    public void collectorRecordQualifierType() {
      var collector = JSONDeserializer.Collector.record(Person.class);
      assertAll(
          () -> assertEquals(String.class, collector.qualifier().apply("name")),
          () -> assertEquals(int.class, collector.qualifier().apply("age"))
      );
    }

    @Test @Tag("Q7")
    public void collectorRecordPreconditions() {
      assertThrows(NullPointerException.class, () -> JSONDeserializer.Collector.record(null));
    }

    public record IntArrayBean(List<Integer> values) { }

    @Test @Tag("Q7")
    public void parseJSONWithABeanAndAList() {
      var deserializer = new JSONDeserializer();
      deserializer.addTypeMatcher(listTypeMatcher());
      deserializer.addTypeMatcher(type -> Optional.of(Utils.erase(type)).filter(Class::isRecord).map(JSONDeserializer.Collector::record));
      var bean = deserializer.parseJSON("""
        {
          "values": [ 12, "foo", 45.2 ]
        }
        """, IntArrayBean.class);
      assertEquals(List.of(12, "foo", 45.2), bean.values);
    }

  }  // end of Q7
}