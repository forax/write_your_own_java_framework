# Injector


- `getInstance(type)`
- `registerInstance(type, object)`
- `registerProvider(type, supplier)`
- `registerProviderClass(type, class)`


```java
record Point(int x, int y) {}
class Circle {
  private final Point center;
  private String name;

  @Inject
  public Circle(Point center) {
    this.center = center;
  }

  @Inject
  public void setName(String name) {
    this.name = name;
  }
}
...
var registry = new Registry();
registry.registerInstance(Point.class, new Point(0, 0));
registry.registerProvider(String.class, () -> "hello");
registry.registerProviderClass(Circle.class, Circle.class);

var circle = registry.getInstance(Circle.class);
System.out.println(circle.center);  // Point(0, 0)
System.out.println(circle.name);  // hello    
```


1. 



