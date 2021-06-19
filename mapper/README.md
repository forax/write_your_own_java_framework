# Mapping objects to JSON

The idea is to implement an object, the `JSONMapper`, that is able to convert an object to a JSON text.

The `JSONMapper` is able to convert
- basic JSON type like boolean, integer or string
- can be configured to handle specific type like `MonthDay` of `java.time`
- recursive types, types composed of other types, likes Java Beans or records 

Here is an example of a `Person` defined as a record, with the `Address` defined as a bean.

```java
class Address {
  private boolean international;
  
  public boolean isInternational() {
    return international;
  }
}
record Person(@JSONProperty("birth-day") MonthDay birthday, Address address) { }
```

We can create a `Mapper`, configure it to use a user defined format for instances of the class `MonthDay`
and calls `toJSON()` to get the corresponding JSON text.

```java
var mapper = new JSONMapper();
mapper.configure(MonthDay.class,
    monthDay -> mapper.toJSON(monthDay.getMonth() + "-" + monthDay.getDayOfMonth()));

var person = new Person(MonthDay.of(4, 17), new Address());
var json = mapper.toJSON(person);  // {"birth-day": "APRIL-17", "address": {"international": false}}
```

The unit tests are here [JSONMapperTest.java](src/test/java/com/github/forax/framework/mapper/JSONMapperTest.java)

1. Create the class `Mapper` and adds the method `toJSON()` that works only with
   JSON primitive values, `null`, `true`, `false`, any integers or doubles and strings.
   Then check that the tests in the nested class "Q1" all pass.

2. Adds a method `configure()` that takes a `Class` and a lambda that takes an instance of that class
   and returns a string and modify `toJSON()` to work with instances of the configured classes.
   Internally, a HashMap that associates a class to the lambda can be used to rapidly find
   the corresponding lambda from an instance of the class.
   Then check that the tests in the nested class "Q2" all pass.
   
   Note: the lambda takes a value and returns a value thus it can be type by a java.util.function.Function.
         The type of the class and the type of the first parameter of the lambda are the same,
         you need to introduce a type parameter for that. Exactly the type of the first parameter of the
         lambda is a super type of the type of the class.

3. Adds the support of Java Beans by modifying `toJSON()` to get the `BeanInfo`.
   Get the properties  from it and use a stream with a `collect(Collectors.joining())`
   to add the '{' and '}' and  separate the values by a comma.
   Then check that the tests in the nested class "Q3" all pass

   Note: the method `Utils.beanInfo()` already provides a way to get the `BeanInfo` of a class.
         the method `Utils.invoke()` can be used to call a `Method`.

4. Modify the code of `toJSON()` to compute the `BeanInfo` and the properties only once per class.
   For that, you will use a private functional interface
   ```java
   private interface Generator {
     String generate(JSONMapper mapper, Object bean);
   }
   ```
   to represent the computation to do to generate a string representation
   of a class and a `ClassValue<Generator>` as a cache.
   All the tests from the previous questions should still pass.

5. JSON keys can use any identifier not only the ones that are valid in Java.
   To support that, add a check if the getter is annotated with the annotation @JSONProperty
   and in that case, use the name provided by the annotation.
   Then check that the tests in the nested class "Q5" all pass

6. Modify the code to support not only Java beans but also records by refactoring
   your code to have two private methods  that takes a Class and returns either the properties of the bean
   or the properties of the records.
   ```java
   private static Stream<PropertyDescriptor> beanProperties(Class<?> type) {
     // TODO
   }

   private static Stream<PropertyDescriptor> recordProperties(Class<?> type) {
     // TODO
   }
   ```
   Change the code so `toJSON()` works with both records and beans.
   Then check that the tests in the nested class "Q1" all pass
