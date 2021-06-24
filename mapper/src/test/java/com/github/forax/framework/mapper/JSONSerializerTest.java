package com.github.forax.framework.mapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"unused", "static-method"})
public class JSONSerializerTest {

  @Nested
  class Q1 {
    @Test
    public void toJSONPrimitive() {
      var serializer = new JSONSerializer();
      assertAll(
          () -> assertEquals("null", serializer.toJSON(null)),
          () -> assertEquals("true", serializer.toJSON(true)),
          () -> assertEquals("false", serializer.toJSON(false)),
          () -> assertEquals("3", serializer.toJSON(3)),
          () -> assertEquals("4.0", serializer.toJSON(4.0)),
          () -> assertEquals("\"foo\"", serializer.toJSON("foo"))
      );
    }
  }

  public static class Car {
    private final String owner;

    public Car(String owner) {
      this.owner = owner;
    }

    public String getOwner() {
      return owner;
    }
  }

  public static class Alien {
    private final String name;
    private final String planet;

    public Alien(String name, String planet) {
      this.name = name;
      this.planet = planet;
    }

    public String getName() {
      return name;
    }

    public String getPlanet() {
      return planet;
    }
  }

  @Nested
  class Q2 {
    @Test
    public void toJSONWithASimpleClass() {
      var serializer = new JSONSerializer();
      var car = new Car("Marty");
      var json = serializer.toJSON(car);
      assertEquals("""
          {"owner": "Marty"}\
          """, json);
    }

    @Test
    public void toJSONWithAClass() {
      var serializer = new JSONSerializer();
      var alien = new Alien("Elvis", "Proxima Centauri");
      var json = serializer.toJSON(alien);
      var expected1 = """
          {"name": "Elvis", "planet": "Proxima Centauri"}\
          """;
      var expected2 = """
          {"planet": "Proxima Centauri", "name": "Elvis"}\
          """;
      assertTrue(
          json.equals(expected1) || json.equals(expected2),
          "error: " + json + "\n expects either " + expected1 + " or " + expected2
      );
    }

    @Test
    public void toJSONEmptyClass() {
      class Empty { }
      var serializer = new JSONSerializer();
      var empty = new Empty();
      var json = serializer.toJSON(empty);
      assertEquals("{}", json);
    }
  }

  public static final class Person {
    private final String firstName;
    private final String lastName;

    public Person(String firstName, String lastName) {
      this.firstName = firstName;
      this.lastName = lastName;
    }

    @JSONProperty("first-name")
    public String getFirstName() {
      return firstName;
    }

    @JSONProperty("last-name")
    public String getLastName() {
      return lastName;
    }
  }

  public static class StartDate {
    private final LocalDateTime time;

    public StartDate(LocalDateTime time) {
      this.time = time;
    }

    public LocalDateTime getTime() {
      return time;
    }
  }


  @Nested
  class Q5 {
    @Test
    public void toJSONWithConfigure() {
      var serializer = new JSONSerializer();
      serializer.configure(LocalDateTime.class, time -> time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
      assertEquals("2021-06-16T20:53:17", serializer.toJSON(LocalDateTime.of(2021, 6, 16, 20, 53, 17)));
    }

    @Test
    public void toJSONBeanWithConfigure() {
      var serializer = new JSONSerializer();
      serializer.configure(LocalDateTime.class, time -> time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
      var startDate = new StartDate(LocalDateTime.of(2021, 7, 1, 20, 7));
      var json = serializer.toJSON(startDate);
      assertEquals("""
      {"time": 2021-07-01T20:07:00}\
      """, json);
    }

    @Test
    public void configureTwice() {
      var serializer = new JSONSerializer();
      serializer.configure(LocalTime.class, __ -> "foo");
      assertThrows(IllegalStateException.class, () -> serializer.configure(LocalTime.class, __ -> "bar"));
    }

    @Test
    public void configurePreconditions() {
      var serializer = new JSONSerializer();
      assertAll(
          () -> assertThrows(NullPointerException.class, () -> serializer.configure(null, String::toString)),
          () -> assertThrows(NullPointerException.class, () -> serializer.configure(Timestamp.class, null))
      );
    }
  }

  @Nested
  class Q6 {
    @Test
    public void toJSONWithJSONProperty() {
      var serializer = new JSONSerializer();
      var person = new Person("Bob", "Hunky");
      var json = serializer.toJSON(person);
      assertEquals("""
          {"first-name": "Bob", "last-name": "Hunky"}\
          """,
          json);
    }
  }

  public static class AddressInfo {
    private boolean international;

    public boolean isInternational() {
      return international;
    }
  }

  public record PersonInfo(@JSONProperty("birth-day") MonthDay birthday, AddressInfo address) { }

  @Nested
  class Q7 {
    @Test
    public void toJSONWithARecord() {
      record Person(String name, int age) { }
      var serializer = new JSONSerializer();
      var person = new Person("Ana", 37);
      var json = serializer.toJSON(person);
      assertEquals("""
          {"name": "Ana", "age": 37}\
          """,
          json);
    }

    @Test
    public void toJSONEmptyRecord() {
      record Empty() { }
      var serializer = new JSONSerializer();
      var empty = new Empty();
      var json = serializer.toJSON(empty);
      assertEquals("{}", json);
    }

    @Test
    public void toJSONRecursive() {
      record Address(String street) { }
      record Person(String name, Address address) { }
      var serializer = new JSONSerializer();
      var person = new Person("Bob", new Address("21 Jump Street"));
      var json = serializer.toJSON(person);
      assertEquals("""
          {"name": "Bob", "address": {"street": "21 Jump Street"}}\
          """,
          json);
    }

    @Test
    public void toJSONFullExample() {
      var serializer = new JSONSerializer();
      serializer.configure(MonthDay.class, monthDay -> serializer.toJSON(monthDay.getMonth() + "-" + monthDay.getDayOfMonth()));
      var person = new PersonInfo(MonthDay.of(4, 17), new AddressInfo());
      var json = serializer.toJSON(person);
      assertEquals("""
          {"birth-day": "APRIL-17", "address": {"international": false}}\
          """,
          json);
    }
  }
}
