# Mapping objects to JSON

```java
record Address(boolean international) {
  public boolean isInternational() {
    return international;
  }
}
record Person(MonthDay birthday, Address address) {
  @JSONProperty("birth-day")
  public MonthDay getBirthday() {
    return birthday;
  }
  public Address getAddress() {
    return address;
  }
}

var mapper = new JSONMapper();
mapper.configure(MonthDay.class, monthDay -> '"' + monthDay.toString() + '"');

var person = new Person(MonthDay.of(4, 17), new Address(false));
var json = mapper.toJSON(person);

assertTrue(
    json.equals("""
       {"address": {"international": false}, "birth-day": "--04-17"}\
       """) ||
    json.equals("""
       {"birth-day": "--04-17", "address": {"international": false}}\
       """)
    );
```



