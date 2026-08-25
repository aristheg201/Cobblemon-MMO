package io.lumine.mythic.lib.util;
import java.util.Objects;import java.util.function.Consumer;
public final class PostLoadAction {private final boolean required;private final Consumer<Object> action;private Object cached;
 public PostLoadAction(Consumer<Object> action){this(false,action);} public PostLoadAction(boolean required,Consumer<Object> action){this.required=required;this.action=Objects.requireNonNull(action);} public void cacheConfig(Object config){cached=config;} public Object getCachedConfig(){return cached;} public void performAction(){if(cached!=null||!required)action.accept(cached);}}
