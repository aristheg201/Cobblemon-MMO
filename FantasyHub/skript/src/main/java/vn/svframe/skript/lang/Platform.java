package vn.svframe.skript.lang;

import java.util.*;

/** Native platform extension points. No Bukkit server, command dispatcher or event objects are required. */
public interface Platform {
    default void requireExecutionThread(){}
    default Map<Values.WorldRef,Integer> worldTimes(){return Map.of();}
    boolean supportsEvent(String event);
    Object property(String property,Object subject,Context context);
    void changeProperty(String property,Object subject,Object value,String mode,Context context);
    boolean supportsProperty(String property,boolean writable);
    Object literal(String name);
    String display(Object value);
    boolean hasPermission(Object subject,String permission);
    boolean isPlayer(Object subject);
    default boolean isEntity(Object subject){return isPlayer(subject)||subject instanceof Values.EntityRef;}
    default boolean isType(Object subject,String type){return type.equals("player")&&isPlayer(subject);}
    void send(Object recipient,String message);
    void broadcast(String message);
    void command(Object sender,String command);
    default void command(Object sender,String command,Context context){command(sender,command);}
    void give(Object recipient,Object item);
    void teleport(Object entity,Object location);
    default void teleport(Object entity,Object location,Context context){teleport(entity,location);}
    void potion(Object entity,String type,int level,Values.Span duration);
    default void potion(Object entity,String type,int level,Values.Span duration,Context context){potion(entity,type,level,duration);}
    default Expression compileExpression(String text,ExpressionCompiler compiler){return null;}
    default Action compileEffect(String text,ExpressionCompiler compiler){return null;}
    default boolean supportsImport(String qualifiedClass){return false;}
    default void error(Syntax.Source source,Throwable failure){}
    default void trace(Syntax.Source source,String kind,Context context){}
    @FunctionalInterface interface Expression {Object evaluate(Context context);}
    @FunctionalInterface interface Action {void execute(Context context);}
    interface Reference extends Expression {default boolean writable(){return true;} void change(Context context,Object value,String mode);}
    final class Context {
        public final Engine engine;
        public final Platform platform;
        public final Map<String,Object> locals=new HashMap<>();
        public final Map<String,Object> event;
        public final Map<String,Object> loops=new HashMap<>();
        public final Map<String,Object> eventState;
        public final long generation;
        public int remaining;
        public int depth;
        public boolean delayed;
        private List<Runnable> undo=new ArrayList<>();
        public void shareTransaction(Context parent){undo=parent.undo;}
        public void rollbackWith(Runnable action){undo.add(action);}
        public void commit(){undo.clear();}
        public void rollback(){RuntimeException failure=null;for(int i=undo.size()-1;i>=0;i--)try{undo.get(i).run();}catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}undo.clear();if(failure!=null)throw failure;}
        public void suspendTransaction(){commit();undo=new ArrayList<>();}
        Context(Engine engine,Map<String,Object> event,Map<String,Object> state,int remaining){
            this.engine=engine;platform=engine.platform();this.event=Collections.unmodifiableMap(new HashMap<>(event));eventState=state;
            generation=engine.generation();this.remaining=remaining;
        }
        public Object event(String key){return event.get(key);}
        public void consume(){if(--remaining<0)throw new IllegalStateException("Script execution budget exceeded");}
        public void cancel(){if(delayed)throw new IllegalStateException("Cannot cancel an event after wait");eventState.put("cancelled",true);}
        public boolean cancelled(){return Boolean.TRUE.equals(eventState.get("cancelled"));}
        public String text(Object value){return platform.display(value);}
    }
}
