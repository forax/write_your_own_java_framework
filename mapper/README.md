# Mapping objects to JSON

The idea is to implement an object, the `JSONMapper`, that is able to convert an object to a JSON text.

The `JSONMapper` is able to convert
- basic JSON type like boolean, integer or string
- can be configured to handle specific type like `MonthDay` of `java.time`
- recursive types, types composed of other types, likes Java Beans or records 

Here is an example of a `Person` defined as a record, with the `Address` defined as a bean,
abd the mapper configured to use a user defined format for instance of the class `MonthDay`.

```java
class Address {
  private boolean international;
  
  public boolean isInternational() {
    return international;
  }
}
record Person(@JSONProperty("birth-day") MonthDay birthday, Address address) { }

var mapper = new JSONMapper();
mapper.configure(MonthDay.class,
    monthDay -> mapper.toJSON(monthDay.getMonth() + "-" + monthDay.getDayOfMonth()));

var person = new Person(MonthDay.of(4, 17), new Address());
var json = mapper.toJSON(person);  // {"birth-day": "APRIL-17", "address": {"international": false}}
```



1. Create the class `Mapper` and adds the method `toJSON()` that works with
   JSON primitive values, `null`, `true`, `false`, any integers or doubles and strings.

2. Adds a method `configure()` that takes a `Class` and a lambda that takes an instance of that class
   and returns a string and modify `toJSON()` to work with instances of the configured classes.
   Internally, a HashMap that associates a class to the lambda can be used to rapidly find
   the corresponding lambda from an instance of the class

3. Adds the support of Java Beans by modifying `toJSON()` to get the `BeanInfo`, get the properties
   from it and use a stream with a `collect(Collectors.joining())` to add the '{' and '}' and
   separate the values by a comma.

4. Modify the code of `toJSON()` to compute the `BeanInfo` and the properties once per class.
   For that, you will use a private functional interface
   ```java
   private interface Generator {
     String generate(JSONMapper mapper, Object bean);
   }
   ```
   to represent the computation to do to generate a string representation
   of a class and a `ClassValue<Generator>` as a cache.

5. Modify the code to support not only Java beans but also records by writing
   two private methods  that takes a Class and properties of the beans or of the records.
   ```java
   private static Stream<PropertyDescriptor> beanProperties(Class<?> type) {
     // TODO
   }

   private static Stream<PropertyDescriptor> recordProperties(Class<?> type) {
     // TODO
   }
   ```
   Then change the code so `toJSON()` works with both records and beans. 
