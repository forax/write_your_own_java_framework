package com.github.forax.framework.orm;

import javax.sql.DataSource;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serial;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Stream;

public final class ORM {
  private ORM() {
    throw new AssertionError();
  }

  @FunctionalInterface
  public interface TransactionBlock {
    void run() throws SQLException;
  }

  private static final Map<Class<?>, String> TYPE_MAPPING = Map.of(
      int.class, "INTEGER",
      Integer.class, "INTEGER",
      long.class, "BIGINT",
      Long.class, "BIGINT",
      String.class, "VARCHAR(255)"
  );

  private static Class<?> findBeanTypeFromRepository(Class<?> repositoryType) {
    var repositorySupertype = Arrays.stream(repositoryType.getGenericInterfaces())
        .flatMap(superInterface -> {
          if (superInterface instanceof ParameterizedType parameterizedType
              && parameterizedType.getRawType() == Repository.class) {
            return Stream.of(parameterizedType);
          }
          return null;
        })
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("invalid repository interface " + repositoryType.getName()));
    var typeArgument = repositorySupertype.getActualTypeArguments()[0];
    if (typeArgument instanceof Class<?> beanType) {
      return beanType;
    }
    throw new IllegalArgumentException("invalid type argument " + typeArgument + " for repository interface " + repositoryType.getName());
  }

  private static class UncheckedSQLException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 42L;

    private UncheckedSQLException(SQLException cause) {
      super(cause);
    }

    @Override
    public SQLException getCause() {
      return (SQLException) super.getCause();
    }
  }


  // --- do not change the code above

  private static final ThreadLocal<Connection> CONNECTION_THREAD_LOCAL = new ThreadLocal<>();

  public static void transaction(DataSource dataSource, TransactionBlock block) throws SQLException {
    Objects.requireNonNull(dataSource);
    Objects.requireNonNull(block);
    try(var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      CONNECTION_THREAD_LOCAL.set(connection);
      try {
        block.run();
        connection.commit();
      } catch(RuntimeException | SQLException e) {
        var cause = (e instanceof UncheckedSQLException unchecked)? unchecked.getCause(): e;
        try {
          connection.rollback();
        } catch(SQLException suppressed) {
          cause.addSuppressed(suppressed);
        }
        throw Utils.rethrow(cause);
      } finally{
        CONNECTION_THREAD_LOCAL.remove();
      }
    }
  }

  // package private for tests
  static Connection currentConnection() {
    var connection = CONNECTION_THREAD_LOCAL.get();
    if (connection == null) {
      throw new IllegalStateException("no connection available");
    }
    return connection;
  }

  // package private for tests
  static String findTableName(Class<?> beanType) {
    var table = beanType.getAnnotation(Table.class);
    var name = table == null? beanType.getSimpleName(): table.value();
    return name.toUpperCase(Locale.ROOT);
  }

  // package private for tests
  static String findColumnName(PropertyDescriptor property) {
    var getter = property.getReadMethod();
    var column = getter.getAnnotation(Column.class);
    var name = column == null? property.getName(): column.value();
    return name.toUpperCase(Locale.ROOT);
  }

  public static void createTable(Class<?> beanType) throws SQLException {
    var beanInfo = Utils.beanInfo(beanType);
    var joiner = new StringJoiner(",\n", "(\n", "\n)");
    for(var property: beanInfo.getPropertyDescriptors()) {
      var propertyName = property.getName();
      if (propertyName.equals("class")) {  // skip, not user defined
        continue;
      }
      var columnName = findColumnName(property);
      var propertyType = property.getPropertyType();
      var typeName = TYPE_MAPPING.get(propertyType);
      if (typeName == null) {
        throw new UnsupportedOperationException("unknown type mapping for type " + propertyType.getName());
      }
      if (propertyType.isPrimitive()) {
        typeName = typeName + " NOT NULL";
      }
      var getter = property.getReadMethod();
      if (getter.isAnnotationPresent(GeneratedValue.class)) {
        typeName += " AUTO_INCREMENT";
      }
      joiner.add(columnName + ' ' + typeName);
      if (getter.isAnnotationPresent(Id.class)) {
        joiner.add("PRIMARY KEY (" + columnName + ')');
      }
    }
    var tableName = findTableName(beanType);
    var sqlQuery = "CREATE TABLE " + tableName + joiner + ";";
    //System.err.println(sqlQuery);
    var connection = currentConnection();
    try(var statement = connection.createStatement()) {
      statement.executeUpdate(sqlQuery);
    }
  }

  // package private for tests
  static Object toEntityClass(ResultSet resultSet, BeanInfo beanInfo, Constructor<?> constructor) throws SQLException {
    var instance = Utils.newInstance(constructor);
    for(var property: beanInfo.getPropertyDescriptors()) {
      var propertyName = property.getName();
      if (propertyName.equals("class")) {
        continue;
      }
      var value = resultSet.getObject(propertyName);
      Utils.invokeMethod(instance, property.getWriteMethod(), value);
    }
    return instance;
  }

  // package private for tests
   static List<Object> findAll(Connection connection, String sqlQuery, BeanInfo beanInfo, Constructor<?> constructor, Object... args) throws SQLException {
    var list = new ArrayList<>();
    try(var statement = connection.prepareStatement(sqlQuery)) {
      //System.err.println(sqlQuery);
      if (args != null) {  // if no argument
        for (var i = 0; i < args.length; i++) {
          statement.setObject(i + 1, args[i]);
        }
      }
      try(var resultSet = statement.executeQuery()) {
        while(resultSet.next()) {
          var instance = toEntityClass(resultSet, beanInfo, constructor);
          list.add(instance);
        }
      }
    }
    return list;
  }

  // package private for tests
  static PropertyDescriptor findId(Class<?> beanType, BeanInfo beanInfo) {
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        .filter(property -> property.getReadMethod().isAnnotationPresent(Id.class))
        .findFirst()
        .orElse(null);
  }

  // package private for tests
  static PropertyDescriptor findProperty(Class<?> beanType, BeanInfo beanInfo, String propertyName) {
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        .filter(property -> property.getName().equals(propertyName))
        .findFirst()
        .orElseThrow(() -> {throw new IllegalStateException("no property " + propertyName + " found for type " + beanType.getName()); });
  }

  @SuppressWarnings("resource")
  public static <T, ID, R extends Repository<T, ID>> R createRepository(Class<? extends R> type) {
    var beanType = findBeanTypeFromRepository(type);
    var beanInfo = Utils.beanInfo(beanType);
    var tableName = findTableName(beanType);
    var constructor = Utils.defaultConstructor(beanType);
    var idProperty = findId(beanType, beanInfo);
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
      var connection = currentConnection();
      var name = method.getName();
      try {
        return switch(name) {
          case "findAll" -> {
            var sqlQuery = "SELECT * FROM " + tableName;
            yield findAll(connection, sqlQuery, beanInfo, constructor);
          }
          case "findById" -> {
            var sqlQuery = "SELECT * FROM " + tableName + " WHERE " + idProperty.getName() + " = ?";
            yield findAll(connection, sqlQuery, beanInfo, constructor, args[0]).stream().findFirst();
          }
          case "save" -> save(connection, tableName, beanInfo, args[0], idProperty);
          case "equals", "hashCode", "toString" -> throw new UnsupportedOperationException("" + method);
          default -> {
            var query = method.getAnnotation(Query.class);
            if (query != null) {
              yield findAll(connection, query.value(), beanInfo, constructor, args);
            }
            if (name.startsWith("findBy")) {
              var propertyName = Introspector.decapitalize(name.substring(6));
              var property = findProperty(beanType, beanInfo, propertyName);
              var sqlQuery = "SELECT * FROM " + tableName + " WHERE " + property.getName() + " = ?";
              yield findAll(connection, sqlQuery, beanInfo, constructor, args[0]).stream().findFirst();
            }
            throw new IllegalStateException("unknown method " + method);
          }
        };
      } catch(SQLException e) {
        throw new UncheckedSQLException(e);
      }
    }));
  }

  // package private for tests
  static String createSaveQuery(String tableName, BeanInfo beanInfo) {
    var values = new StringJoiner(", ", "(", ")");
    var columns = new StringJoiner(", ", "(", ")");
    for(var property: beanInfo.getPropertyDescriptors()) {
      var propertyName = property.getName();
      if (propertyName.equals("class")) {
        continue;
      }
      values.add("?");
      columns.add(propertyName);
    }
    return "MERGE INTO " + tableName + " " + columns + " VALUES " + values + ";";
  }

  static Object save(Connection connection, String tableName, BeanInfo beanInfo, Object bean, PropertyDescriptor idProperty) throws SQLException {
    String sqlQuery = createSaveQuery(tableName, beanInfo);
    //System.err.println(sqlQuery);

    try(var statement = connection.prepareStatement(sqlQuery, Statement.RETURN_GENERATED_KEYS)) {
      var index = 1;
      for(var property: beanInfo.getPropertyDescriptors()) {
        if (property.getName().equals("class")) {
          continue;
        }
        statement.setObject(index++, Utils.invokeMethod(bean, property.getReadMethod()));
      }
      statement.executeUpdate();
      if (idProperty != null) {
        try(var resultSet = statement.getGeneratedKeys()) {
          if (resultSet.next()) {
            var key = resultSet.getObject( 1);
            Utils.invokeMethod(bean, idProperty.getWriteMethod(), key);
          }
        }
      }
    }
    return bean;
  }
}
