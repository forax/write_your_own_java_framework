package com.github.forax.framework.injector.scanner;

import com.github.forax.framework.injector.Inject;

public class OnlyConstructor {
  @Inject
  public OnlyConstructor(Dependency dependency) {}
}
