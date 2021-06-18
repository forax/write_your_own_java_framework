package com.github.forax.framework.mapper;

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
public class JSONMapperTest {

  @Test
  public void toJSONPrimitive() {
    var mapper = new JSONMapper();
    assertAll(
        () -> assertEquals("null", mapper.toJSON(null)),
        () -> assertEquals("true", mapper.toJSON(true)),
        () -> assertEquals("false", mapper.toJSON(false)),
        () -> assertEquals("3", mapper.toJSON(3)),
        () -> assertEquals("4.0", mapper.toJSON(4.0)),
        () -> assertEquals("\"foo\"", mapper.toJSON("foo"))
    );
  }

  @Test
  public void toJSONWithConfigure() {
    var mapper = new JSONMapper();
    mapper.configure(LocalDateTime.class, time -> time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    assertEquals("2021-06-16T20:53:17", mapper.toJSON(LocalDateTime.of(2021, 6, 16, 20, 53, 17)));
  }

  @Test
  public void configureTwice() {
    var mapper = new JSONMapper();
    mapper.configure(LocalTime.class, __ -> "foo");
    assertThrows(IllegalStateException.class, () -> mapper.configure(LocalTime.class, __ -> "bar"));
  }

  @Test
  public void configurePreconditions() {
    var mapper = new JSONMapper();
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> mapper.configure(null, String::toString)),
        () -> assertThrows(NullPointerException.class, () -> mapper.configure(Timestamp.class, null))
    );
  }

  static class Alien {
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


  @Test
  public void toJSONWithAClass() {
    var mapper = new JSONMapper();
    var alien = new Alien("Elvis", "Proxima Centauri");
    var json = mapper.toJSON(alien);
    assertTrue(
        json.equals("""
          {"name": "Elvis", "planet": "Proxima Centauri"}\
          """) ||
        json.equals("""
          {"planet": "Proxima Centauri", "name": "Elvis"}\
          """)
    );
  }

  @Test
  public void toJSONWithARecord() {
    record Person(String name, int age) { }
    var mapper = new JSONMapper();
    var person = new Person("Ana", 37);
    var json = mapper.toJSON(person);
    assertEquals("""
          {"name": "Ana", "age": 37}\
          """,
          json);
  }

  @Test
  public void toJSONEmptyClass() {
    class Empty { }
    var mapper = new JSONMapper();
    var empty = new Empty();
    var json = mapper.toJSON(empty);
    assertEquals("{}", json);
  }

  @Test
  public void toJSONEmptyRecord() {
    record Empty() { }
    var mapper = new JSONMapper();
    var empty = new Empty();
    var json = mapper.toJSON(empty);
    assertEquals("{}", json);
  }

  @Test
  public void toJSONBeanWithConfigure() {
    record StartDate(LocalDateTime time) {
      public LocalDateTime getTime() {
        return time;
      }
    }
    var mapper = new JSONMapper();
    mapper.configure(LocalDateTime.class, time -> time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    var startDate = new StartDate(LocalDateTime.of(2021, 7, 1, 20, 7));
    var json = mapper.toJSON(startDate);
    assertEquals("""
      {"time": 2021-07-01T20:07:00}\
      """, json);
  }

  @Test
  public void toJSONRecursive() {
    record Address(String street) { }
    record Person(String name, Address address) { }
    var mapper = new JSONMapper();
    var person = new Person("Bob", new Address("21 Jump Street"));
    var json = mapper.toJSON(person);
    assertEquals("""
          {"name": "Bob", "address": {"street": "21 Jump Street"}}\
          """,
          json);
  }

  @Test
  public void toJSONWithJSONProperty() {
    record Person(@JSONProperty("first-name") String firstName, @JSONProperty("last-name") String lastName) { }
    var mapper = new JSONMapper();
    var person = new Person("Bob", "Hunky");
    var json = mapper.toJSON(person);
    assertEquals("""
          {"first-name": "Bob", "last-name": "Hunky"}\
          """,
          json);
  }

  static class AddressInfo {
    private boolean international;

    public boolean isInternational() {
      return international;
    }
  }
  record PersonInfo(@JSONProperty("birth-day") MonthDay birthday, AddressInfo address) { }

  @Test
  public void toJSONFullExample() {
    var mapper = new JSONMapper();
    mapper.configure(MonthDay.class, monthDay -> mapper.toJSON(monthDay.getMonth() + "-" + monthDay.getDayOfMonth()));
    var person = new PersonInfo(MonthDay.of(4, 17), new AddressInfo());
    var json = mapper.toJSON(person);
    assertEquals("""
          {"birth-day": "APRIL-17", "address": {"international": false}}\
          """,
          json);
  }
}