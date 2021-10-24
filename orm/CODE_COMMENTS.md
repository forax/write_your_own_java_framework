# Code comments

### Q1

```java
  private static final ThreadLocal<Connection> CONNECTION_THREAD_LOCAL = new ThreadLocal<>();

  public static void transaction(DataSource dataSource, TransactionBlock block) throws SQLException {
    Objects.requireNonNull(dataSource);
    Objects.requireNonNull(block);
    try(var connection = dataSource.getConnection()) {
      CONNECTION_THREAD_LOCAL.set(connection);
      try {
        block.run();
      } finally {
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
```

### Q2

```java
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
        try {
          connection.rollback();
        } catch(SQLException suppressed) {
          e.addSuppressed(suppressed);
        }
        throw Utils.rethrow(e);
      } finally{
        CONNECTION_THREAD_LOCAL.remove();
      }
    }
  }
```

### Q3

```java
  static String findTableName(Class<?> beanType) {
    var table = beanType.getAnnotation(Table.class);
    var name = table == null? beanType.getSimpleName(): table.value();
    return name.toUpperCase(Locale.ROOT);
  }

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
      var typeName = "VARCHAR(255)";
      joiner.add(columnName + ' ' + typeName);
    }
    var tableName = findTableName(beanType);
    var sqlQuery = "CREATE TABLE " + tableName + joiner + ";";
    var connection = currentConnection();
    try(var statement = connection.createStatement()) {
      statement.executeUpdate(sqlQuery);
    }
  }
```

### Q4

```java
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
        typeName += " NOT NULL";
      }
      joiner.add(columnName + ' ' + typeName);
    }
    var tableName = findTableName(beanType);
    var sqlQuery = "CREATE TABLE " + tableName + joiner + ";";
    var connection = currentConnection();
    try(var statement = connection.createStatement()) {
      statement.executeUpdate(sqlQuery);
    }
  }
```

### Q5

```java
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
        typeName += " NOT NULL";
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
    var connection = currentConnection();
    try(var statement = connection.createStatement()) {
      statement.executeUpdate(sqlQuery);
    }
  }
```

### Q6

```java
  public static <T, ID, R extends Repository<T, ID>> R createRepository(Class<? extends R> type) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
        var connection = currentConnection();
        var name = method.getName();
        return switch(name) {
          case "findAll" -> List.of();   // for now
          case "equals", "hashCode", "toString" -> throw new UnsupportedOperationException("" + method);
          default -> throw new IllegalStateException("unknown method " + method);
        };
    }));
  }
```

### Q7

```java
  private static Class<?> findBeanType(Class<?> repositoryType) {
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

  static List<Object> findAll(Connection connection, String sqlQuery, BeanInfo beanInfo, Constructor<?> constructor)
      throws SQLException {
    var list = new ArrayList<>();
    try(var statement = connection.prepareStatement(sqlQuery)) {
      try(var resultSet = statement.executeQuery()) {
        while(resultSet.next()) {
          var instance = toEntityClass(resultSet, beanInfo, constructor);
          list.add(instance);
        }
      }
    }
    return list;
  }

  public static <T, ID, R extends Repository<T, ID>> R createRepository(Class<? extends R> type) {
    var beanType = findBeanTypeFromRepository(type);
    var beanInfo = Utils.beanInfo(beanType);
    var tableName = findTableName(beanType);
    var constructor = Utils.defaultConstructor(beanType);
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
      var connection = currentConnection();
      var name = method.getName();
      try {
        return switch(name) {
          case "findAll" -> {
            var sqlQuery = "SELECT * FROM " + tableName;
            yield findAll(connection, sqlQuery, beanInfo, constructor);
          }
          case "equals", "hashCode", "toString" -> throw new UnsupportedOperationException("" + method);
          default -> throw new IllegalStateException("unknown method " + method);
        };
      } catch(SQLException e) {
        throw new UncheckedSQLException(e);
      }
    }));
  }

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
```

### Q8

```java
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
    return "INSERT INTO " + tableName + " " + columns + " VALUES " + values + ";";
  }

  static Object save(Connection connection, String tableName, BeanInfo beanInfo, Object bean, PropertyDescriptor idProperty)
        throws SQLException {
    var sqlQuery = createSaveQuery(tableName, beanInfo);
    try(var statement = connection.prepareStatement(sqlQuery, Statement.RETURN_GENERATED_KEYS)) {
      var index = 1;
      for(var property: beanInfo.getPropertyDescriptors()) {
        if (property.getName().equals("class")) {
          continue;
        }
        statement.setObject(index++, Utils.invokeMethod(bean, property.getReadMethod()));
      }
      statement.executeUpdate();
    }
    return bean;
  }

  public static <T, ID, R extends Repository<T, ID>> R createRepository(Class<? extends R> type) {
    var beanType = findBeanTypeFromRepository(type);
    var beanInfo = Utils.beanInfo(beanType);
    var tableName = findTableName(beanType);
    var constructor = Utils.defaultConstructor(beanType);
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
      var connection = currentConnection();
      var name = method.getName();
      try {
        return switch(name) {
          case "findAll" -> {
            var sqlQuery = "SELECT * FROM " + tableName;
            yield findAll(connection, sqlQuery, beanInfo, constructor);
          }
          case "equals", "hashCode", "toString" -> throw new UnsupportedOperationException("" + method);
          case "save" -> save(connection, tableName, beanInfo, args[0]);
          default -> throw new IllegalStateException("unknown method " + method);
        };
      } catch(SQLException e) {
        throw new UncheckedSQLException(e);
      }
    }));
  }  
```

### Q9

```java
  static Object save(Connection connection, String tableName, BeanInfo beanInfo, Object bean, PropertyDescriptor idProperty)
      throws SQLException {
    var sqlQuery = createSaveQuery(tableName, beanInfo);
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

  private static PropertyDescriptor findId(Class<?> beanType, BeanInfo beanInfo) {
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        .filter(property -> property.getReadMethod().isAnnotationPresent(Id.class))
        .findFirst()
        .orElseThrow(() -> { throw new IllegalStateException("no property annotated with @id found for type " + beanType.getName()); });
  }
  
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
          case "equals", "hashCode", "toString" -> throw new UnsupportedOperationException("" + method);
          case "save" -> save(connection, tableName, beanInfo, args[0], idProperty);
          default -> throw new IllegalStateException("unknown method " + method);
        };
      } catch(SQLException e) {
        throw new UncheckedSQLException(e);
      }
    }));
  }  
```

### Q10

```java
  private static String createSaveQuery(String tableName, BeanInfo beanInfo) {
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
```

### Q11

```java
  private static List<Object> findAll(Connection connection, String sqlQuery, BeanInfo beanInfo, Constructor<?> constructor, Object... args) throws SQLException {
    var list = new ArrayList<>();
    try(var statement = connection.prepareStatement(sqlQuery)) {
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
          default -> throw new IllegalStateException("unknown method " + method);
        };
      } catch(SQLException e) {
        throw new UncheckedSQLException(e);
      }
    }));
  }
```

### Q12

```java
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
            throw new IllegalStateException("unknown method " + method);
          }
        };
      } catch(SQLException e) {
        throw new UncheckedSQLException(e);
      }
    }));
  }
```

### Q13

```java
  private static PropertyDescriptor findProperty(Class<?> beanType, BeanInfo beanInfo, String propertyName) {
    return Arrays.stream(beanInfo.getPropertyDescriptors())
        .filter(property -> property.getName().equals(propertyName))
        .findFirst()
        .orElseThrow(() -> {throw new IllegalStateException("no property " + propertyName + " found for type " + beanType.getName()); });
  }
    
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
              yield find(connection, query.value(), beanInfo, constructor, args);
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
```