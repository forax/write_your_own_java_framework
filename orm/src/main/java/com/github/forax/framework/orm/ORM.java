package com.github.forax.framework.orm;

import javax.sql.DataSource;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class ORM {
  private ORM() {
    throw new AssertionError();
  }

  private static final ThreadLocal<Connection> CONNECTION_THREAD_LOCAL = new ThreadLocal<>();

  private static class UncheckedSQLException extends RuntimeException {
    private static final long serialVersionUID = 42L;

    private UncheckedSQLException(SQLException cause) {
      super(cause);
    }

    @Override
    public SQLException getCause() {
      return (SQLException) super.getCause();
    }
  }

  public static void transaction(DataSource dataSource, Runnable runnable) throws SQLException {
    Objects.requireNonNull(dataSource);
    Objects.requireNonNull(runnable);
    try(var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      CONNECTION_THREAD_LOCAL.set(connection);
      try {
        runnable.run();
        connection.commit();
      } finally{
        CONNECTION_THREAD_LOCAL.remove();
      }
    } catch(UncheckedSQLException e) {
      throw e.getCause();
    }
  }

  private static List<Object> find(Connection connection, String sqlQuery, Class<?> beanType, Constructor<?> constructor, BeanInfo beanInfo, Object... args) throws SQLException {
    var isInterface = beanType.isInterface();
    var list = new ArrayList<>();
    try(var statement = connection.prepareStatement(sqlQuery)) {
      System.err.println(sqlQuery);
      for (var i = 0; i < args.length; i++) {
        statement.setObject(i + 1, args[i]);
      }
      try(var resultSet = statement.executeQuery()) {
        while(resultSet.next()) {
          var instance = isInterface?
              toEntityProxy(resultSet, beanType, beanInfo):
              toEntityClass(resultSet, constructor, beanInfo);
          list.add(instance);
        }
      }
    }
    return list;
  }

  private static Object toEntityClass(ResultSet resultSet, Constructor<?> constructor, BeanInfo beanInfo) throws SQLException {
    var instance = Utils.newInstance(constructor);
    for(var property: beanInfo.getPropertyDescriptors()) {
      var propertyName = property.getName();
      if (propertyName.equals("class")) {
        continue;
      }
      Object value = resultSet.getObject(propertyName /*, property.getPropertyType()*/);
      Utils.invoke(instance, property.getWriteMethod(), value);
    }
    return instance;
  }

  private static Object toEntityProxy(ResultSet resultSet, Class<?> beanType, BeanInfo beanInfo) throws SQLException {
    var properties = beanInfo.getPropertyDescriptors();
    var array = new Object[properties.length];
    for (int i = 0; i < properties.length; i++) {
      array[i] = resultSet.getObject(i + 1);  // ResultSet arguments start at 1
    }
    return newEntityProxy(beanType, array);
  }

  private static Object copyToEntityProxy(Object bean, Class<?> beanType, BeanInfo beanInfo) {
    var properties = beanInfo.getPropertyDescriptors();
    var array = new Object[properties.length];
    for (int i = 0; i < properties.length; i++) {
      var property = properties[i];
      array[i] = Utils.invoke(bean, property.getReadMethod());
    }
    return newEntityProxy(beanType, array);
  }

  private static Object newEntityProxy(Class<?> beanType, Object[] array) {
    var behaviorMap = BEHAVIOR_CLASS_VALUE.get(beanType);
    return Proxy.newProxyInstance(beanType.getClassLoader(),
        new Class<?>[] { beanType },
        new TrackingInvocationHandler(behaviorMap, array));
  }

  private static Object save(Connection connection, String tableName, Class<?> beanType, BeanInfo beanInfo, PropertyDescriptor idProperty, Object bean) throws SQLException {
    var trackingInvocationHandler = TrackingInvocationHandler.asTrackingInvocationHandler(bean);
    var properties = (trackingInvocationHandler == null)? beanInfo.getPropertyDescriptors(): trackingInvocationHandler.dirtySet(beanInfo, idProperty);
    var values = new StringJoiner(", ", "(", ")");
    var columns = new StringJoiner(", ", "(", ")");
    for(var property: properties) {
      var propertyName = property.getName();
      if (propertyName.equals("class")) {
        continue;
      }
      values.add("?");
      columns.add(propertyName);
    }
    var sqlQuery = "MERGE INTO " + tableName + " " + columns + " VALUES " + values + ";";

    System.err.println(sqlQuery);
    Object result = bean;
    try(var statement = connection.prepareStatement(sqlQuery, Statement.RETURN_GENERATED_KEYS)) {
      var index = 1;
      for(var property: properties) {
        if (property.getName().equals("class")) {
          continue;
        }
        statement.setObject(index++, Utils.invoke(bean, property.getReadMethod()));
      }
      statement.executeUpdate();
      if (beanType.isInterface() && trackingInvocationHandler == null) {
        result = copyToEntityProxy(bean, beanType, beanInfo);
      }
      try(var resultSet = statement.getGeneratedKeys()) {
        if (resultSet.next()) {
          var key = resultSet.getObject( 1);
          Utils.invoke(result, idProperty.getWriteMethod(), key);
        }
      }
    }
    if (trackingInvocationHandler != null) {
      trackingInvocationHandler.dirtySet.clear();
    }
    return result;
  }

  private static Class<?> findBeanType(Class<?> repositoryInterface) {
    var repositoryType = Arrays.stream(repositoryInterface.getGenericInterfaces())
        .flatMap(superInterface -> superInterface instanceof ParameterizedType parameterizedType? Stream.of(parameterizedType): Stream.empty())
        .filter(superInterface -> superInterface.getRawType() == Repository.class)
        .findFirst().orElseThrow(() -> new IllegalArgumentException("invalid repository interface " + repositoryInterface.getName()));
    var typeArgument = repositoryType.getActualTypeArguments()[0];
    if (typeArgument instanceof Class<?> beanType) {
      return beanType;
    }
    throw new IllegalArgumentException("invalid type argument " + typeArgument + " for repository interface " + repositoryInterface.getName());
  }

  private static PropertyDescriptor findProperty(Class<?> beanType, BeanInfo beanInfo, String propertyName) {
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        .filter(property -> property.getName().equals(propertyName))
        .findFirst()
        .orElseThrow(() -> {throw new IllegalStateException("no property " + propertyName + " found for type " + beanType.getName()); });
  }

  private static PropertyDescriptor findId(Class<?> beanType, BeanInfo beanInfo) {
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        .filter(property -> property.getReadMethod().isAnnotationPresent(Id.class))
        .findFirst()
        .orElseThrow(() -> {throw new IllegalStateException("no property annotated with @id found for type " + beanType.getName()); });
  }

  // package private for tests
  static Connection currentConnection() {
    var connection = CONNECTION_THREAD_LOCAL.get();
    if (connection == null) {
      throw new IllegalStateException("no connection available");
    }
    return connection;
  }

  private static Constructor<?> findDefaultConstructor(Class<?> beanType) {
    try {
      return beanType.getConstructor();
    } catch (NoSuchMethodException e) {
      throw (NoSuchMethodError) new NoSuchMethodError("no public default constructor").initCause(e);
    }
  }

  private static String findTableName(Class<?> beanType) {
    Table table = beanType.getAnnotation(Table.class);
    if (table == null) {
      return beanType.getSimpleName().toUpperCase(Locale.ROOT);
    }
    return table.value().toUpperCase(Locale.ROOT);
  }

  @SuppressWarnings("resource")
  public static <T, ID, R extends Repository<T, ID>> R createRepository(Class<? extends R> type) {
    var beanType = findBeanType(type);
    var beanInfo = Utils.beanInfo(beanType);
    var idProperty = findId(beanType, beanInfo);
    var tableName = findTableName(beanType);
    var constructor = beanType.isInterface()? null: findDefaultConstructor(beanType);

    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
      var connection = currentConnection();
      var name = method.getName();
      try {
        return switch(name) {
          case "findAll" -> find(connection, "SELECT * FROM " + tableName, beanType, constructor, beanInfo);
          case "findById" -> find(connection, "SELECT * FROM " + tableName +" WHERE " + idProperty.getName() + " = ?" , beanType, constructor, beanInfo, args[0]).stream().findFirst();
          case "save" -> save(connection, tableName, beanType, beanInfo, idProperty, args[0]);
          case "equals", "hashCode", "toString" -> throw new UnsupportedOperationException("" + method);
          default -> {
            var query = method.getAnnotation(Query.class);
            if (query != null) {
              yield find(connection, query.value(), beanType, constructor, beanInfo, args);
            }
            if (name.startsWith("findBy")) {
              var propertyName = Introspector.decapitalize(name.substring(6));
              var property = findProperty(beanType, beanInfo, propertyName);
              yield find(connection, "SELECT * FROM " + tableName +" WHERE " + property.getName() + " = ?", beanType, constructor, beanInfo, args[0]).stream().findFirst();
            }
            throw new NoSuchMethodError("" + method);
          }
        };
      } catch(SQLException e) {
        throw new UncheckedSQLException(e);
      }
    }));
  }

  private interface Behavior {
    Object apply(TrackingInvocationHandler handler, Object[] args);
  }

  private static final Method TO_STRING;
  static {
    try {
      TO_STRING = Object.class.getMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }
  private static final ClassValue<HashMap<Method, Behavior>> BEHAVIOR_CLASS_VALUE = new ClassValue<>() {
    @Override
    protected HashMap<Method, Behavior> computeValue(Class<?> beanType) {
      var beanInfo = Utils.beanInfo(beanType);
      var map = new HashMap<Method, Behavior>();
      var properties = beanInfo.getPropertyDescriptors();
      for (int i = 0; i < properties.length; i++) {
        var property = properties[i];
        var index = i;
        map.put(property.getReadMethod(), (handler, args) -> handler.data[index]);
        map.put(property.getWriteMethod(), (handler, args) -> {
          var argument = args[0];
          Objects.requireNonNull(argument);
          handler.data[index] = argument;
          handler.dirtySet.set(index);
          return null;
        });
      }
      map.put(TO_STRING, (handler, args) -> IntStream.range(0, properties.length)
            .mapToObj(i -> properties[i].getName() + ": " + handler.data[i])
            .collect(Collectors.joining(", ", beanType.getSimpleName()  + "{", "}")));
      return map;
    }
  };

  private static class TrackingInvocationHandler implements InvocationHandler {
    private final HashMap<Method, Behavior> behaviorMap;
    private final Object[] data;
    private final BitSet dirtySet = new BitSet();

    public TrackingInvocationHandler(HashMap<Method, Behavior> behaviorMap, Object[] data) {
      this.behaviorMap = behaviorMap;
      this.data = data;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      var behavior = behaviorMap.get(method);
      if (behavior == null) {
        throw new NoSuchMethodError("no implementation for method " + method + " available");
      }
      return behavior.apply(this, args);
    }

    public PropertyDescriptor[] dirtySet(BeanInfo beanInfo, PropertyDescriptor idProperty) {
      var properties = beanInfo.getPropertyDescriptors();
      var set = new LinkedHashSet<PropertyDescriptor>();
      set.add(idProperty);  // always dirty !
      for(var i = dirtySet.nextSetBit(0); i != -1; i = dirtySet.nextSetBit(i + 1)) {
        set.add(properties[i]);
      }
      return set.toArray(PropertyDescriptor[]::new);
    }

    public static TrackingInvocationHandler asTrackingInvocationHandler(Object bean) {
      if (!Proxy.isProxyClass(bean.getClass())) {
        return null;
      }
      var invocationHandler = Proxy.getInvocationHandler(bean);
      if (invocationHandler instanceof TrackingInvocationHandler trackingInvocationHandler) {
        return trackingInvocationHandler;
      }
      return null;
    }
  }

  private static final Map<Class<?>, String> TYPE_MAPPING = Map.of(
    int.class, "INTEGER NOT NULL",
    Long.class, "BIGINT",
    String.class, "VARCHAR(255)"
  );

  public static void createTable(Class<?> beanType) {
    var beanInfo = Utils.beanInfo(beanType);
    var tableName = findTableName(beanType);
    var connection = currentConnection();
    var joiner = new StringJoiner(",\n", "(\n", "\n)");
    for(var property: beanInfo.getPropertyDescriptors()) {
      var propertyName = property.getName();
      if (propertyName.equals("class")) {
        continue;
      }
      var typeName = TYPE_MAPPING.get(property.getPropertyType());
      if (typeName == null) {
        throw new UnsupportedOperationException("unknown type mapping for type " + property.getPropertyType().getName());
      }
      var getter = property.getReadMethod();
      if (getter.isAnnotationPresent(GeneratedValue.class)) {
        typeName += " AUTO_INCREMENT";
      }
      joiner.add(propertyName + ' ' + typeName);
      if (getter.isAnnotationPresent(Id.class)) {
        joiner.add("PRIMARY KEY (" + propertyName + ')');
      }
    }
    var sqlQuery = "CREATE TABLE " + tableName + joiner + ";";
    System.err.println(sqlQuery);
    try(var statement = connection.createStatement()) {
      statement.executeUpdate(sqlQuery);
    } catch (SQLException e) {
      throw new UncheckedSQLException(e);
    }
  }
}
