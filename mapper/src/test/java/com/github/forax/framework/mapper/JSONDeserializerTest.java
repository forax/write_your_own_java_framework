package com.github.forax.framework.mapper;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class JSONDeserializerTest {

  public static class SimpleBean {
    private String name;

    public void setName(String name) {
      this.name = name;
    }
  }

  @Test
  public void parseJSON() {
    var deserializer = new JSONDeserializer();
    var bean = deserializer.parseJSON("""
        {
          "name": "Bob"
        }
        """, SimpleBean.class);
    assertEquals("Bob", bean.name);
  }
}