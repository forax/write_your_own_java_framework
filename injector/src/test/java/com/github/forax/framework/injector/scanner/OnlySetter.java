package com.github.forax.framework.injector.scanner;

import com.github.forax.framework.injector.Inject;

public class OnlySetter {
  @Inject
  public void setDependency(Dependency dependency) {}
}
