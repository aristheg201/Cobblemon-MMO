package vn.svframe.skript.lang;

import java.util.*;
import java.util.function.LongSupplier;
import vn.svframe.skript.lang.Platform.*;
import vn.svframe.skript.lang.Program.*;

/** Bounded server-thread VM. Suspensions retain local variables and loop frames, not stale entity objects. */
public final class Engine {
    private final Platform platform;
    private final Variables variables=new Variables();
    private Program program=new Program(Map.of());
    private final PriorityQueue<Task> scheduled=new PriorityQueue<>(Comparator.comparingLong((Task t)->t.at).thenComparingLong(t->t.order));
    private final Map<Trigger,Long> periodic=new LinkedHashMap<>();
    private List<Trigger> timed=List.of(),periodicOrder=List.of();
    private final Map<Values.WorldRef,Integer> lastWorldTimes=new HashMap<>();
    private LongSupplier clock=System::currentTimeMillis;
    private long generation,tick,sequence;
    private int tickRemaining=250000,dispatchDepth;
    public Engine(Platform platform){this.platform=platform;}
    public Platform platform(){return platform;}
    public Variables variables(){return variables;}
    public Program program(){return program;}
    public long generation(){return generation;}
    public long now(){return clock.getAsLong();}
    public Object evaluate(Platform.Expression expression){return expression.evaluate(new Context(this,Map.of(),new HashMap<>(),50000));}
    /** Invoke an installed source function from a native server integration with the same transaction semantics as a command. */
    public Object invokeFunction(String name,List<Object> arguments){
        platform.requireExecutionThread();
        Context parent=new Context(this,Map.of(),new HashMap<>(),50000);
        try{Object value=call(name,arguments,parent);parent.commit();return value;}
        catch(RuntimeException failure){try{parent.rollback();}catch(RuntimeException rollbackFailure){failure.addSuppressed(rollbackFailure);}throw failure;}
    }
    public void clock(LongSupplier clock){this.clock=clock;}
    public int pendingTasks(){return scheduled.size();}
    public void install(Program next){
        platform.requireExecutionThread();
        dispatch("unload",Map.of());generation++;scheduled.clear();periodic.clear();timed=List.of();periodicOrder=List.of();lastWorldTimes.clear();program=next;
        List<Trigger> nextTimed=new ArrayList<>();
        Context defaults=new Context(this,Map.of(),new HashMap<>(),10000);
        for(File file:program.files.values())if(file.valid()){
            file.defaults().forEach((name,e)->{if(variables.get(name,defaults.locals)==null)variables.set(name,e.evaluate(defaults),defaults.locals);});
            for(Trigger trigger:file.triggers())if(trigger.period()>0)periodic.put(trigger,tick+trigger.period());else if(trigger.atTime()>=0)nextTimed.add(trigger);
        }
        timed=List.copyOf(nextTimed);periodicOrder=List.copyOf(periodic.keySet());
        if(!timed.isEmpty())platform.worldTimes().forEach((world,time)->lastWorldTimes.put(world,Math.floorMod(time-1,24000)));
        dispatch("load",Map.of());
    }
    public void stop(){platform.requireExecutionThread();dispatch("unload",Map.of());generation++;scheduled.clear();periodic.clear();timed=List.of();periodicOrder=List.of();lastWorldTimes.clear();}
    public Map<String,Object> dispatch(String event,Map<String,Object> values){
        platform.requireExecutionThread();
        if(dispatchDepth>=16)throw new IllegalStateException("Script event recursion exceeds 16");
        dispatchDepth++;
        try {
            long currentGeneration=generation;
            Map<String,Object> state=new HashMap<>();state.put("cancelled",false);
            for(File file:program.files.values())if(file.valid())for(Trigger trigger:file.triggers()){
                if(currentGeneration!=generation)return state;
                if(trigger.event().equalsIgnoreCase(event))execute(trigger.body(),new Context(this,values,state,50000),trigger.source());
            }
            return state;
        }finally{dispatchDepth--;}
    }
    public void tick(){
        platform.requireExecutionThread();
        tick++;tickRemaining=250000;
        long currentGeneration=generation;
        if(!timed.isEmpty()&&tick%10==0)checkWorldTimes();
        if(currentGeneration!=generation)return;
        for(Trigger trigger:periodicOrder){
            if(currentGeneration!=generation)return;
            if(periodic.get(trigger)<=tick){periodic.put(trigger,tick+trigger.period());execute(trigger.body(),new Context(this,Map.of(),new HashMap<>(),50000),trigger.source());}
        }
        int resumed=0;
        while(!scheduled.isEmpty()&&scheduled.peek().at<=tick&&resumed++<512&&tickRemaining>0){Task task=scheduled.poll();if(task.context.generation!=generation)continue;task.detached=true;task.context.remaining=50000;run(task);}
    }
    /** Match the supplied Skript2.13.2 clock window, including day wrap and explicit time jumps. */
    private void checkWorldTimes(){
        long currentGeneration=generation;
        Map<Values.WorldRef,Integer> current=platform.worldTimes();lastWorldTimes.keySet().retainAll(current.keySet());
        current.forEach((world,raw)->{
            if(currentGeneration!=generation)return;
            int now=Math.floorMod(raw,24000),last=lastWorldTimes.getOrDefault(world,Math.floorMod(now-1,24000));
            int elapsed=Math.floorMod(now-last,24000);if(elapsed==0)return;
            if(elapsed>20){last=Math.floorMod(now-10,24000);elapsed=10;}
            for(Trigger trigger:timed){if(currentGeneration!=generation)return;int delta=Math.floorMod(trigger.atTime()-last,24000);
                if(delta>0&&delta<=elapsed)execute(trigger.body(),new Context(this,Map.of("world",world,"event-world",world),new HashMap<>(),50000),trigger.source());
            }
            if(currentGeneration==generation)lastWorldTimes.put(world,now);
        });
    }
    public Object call(String name,List<Object> arguments,Context parent){
        return call(name,arguments,parent,"");
    }
    public Object call(String name,List<Object> arguments,Context parent,String sourceFile){
        platform.requireExecutionThread();
        Function fn=program.function(name,sourceFile);if(fn==null)throw new IllegalArgumentException("Unavailable function: "+name);
        if(arguments.size()!=fn.parameters().size())throw new IllegalArgumentException("Function "+name+" expects "+fn.parameters().size()+" arguments");
        if(parent.depth>=32)throw new IllegalStateException("Function call depth exceeds 32");
        Context context=new Context(this,parent.event,parent.eventState,parent.remaining);context.depth=parent.depth+1;context.delayed=parent.delayed;context.shareTransaction(parent);
        for(int i=0;i<arguments.size();i++){
            Object value=arguments.get(i);if(value!=null&&!validType(value,fn.parameters().get(i).type()))throw new IllegalArgumentException("Wrong argument type for "+name+": "+fn.parameters().get(i).name());
            context.locals.put("_"+fn.parameters().get(i).name().toLowerCase(Locale.ROOT),Values.copy(value));
        }
        Task task=new Task(context,fn.source());task.frames.push(new BlockFrame(fn.body()));run(task);parent.remaining=context.remaining;return task.result;
    }
    private boolean validType(Object v,String type){return switch(type){case "text","string"->v instanceof String;case "number","integer"->v instanceof Number;case "boolean"->v instanceof Boolean;case "player"->platform.isPlayer(v);case "location"->v instanceof Values.Location;case "object"->true;default->true;};}
    public boolean command(Command command,Object sender,List<Object> arguments){
        platform.requireExecutionThread();
        if(!command.permission().isBlank()&&!platform.hasPermission(sender,command.permission())){platform.send(sender,command.denied().isBlank()?"Missing permission: "+command.permission():command.denied());return false;}
        String executors=command.executableBy().strip().toLowerCase(Locale.ROOT);
        if((executors.equals("players")||executors.equals("player"))&&!platform.isPlayer(sender))return false;
        if(executors.equals("console")&&platform.isPlayer(sender))return false;
        if(arguments.size()<command.required()||arguments.size()>command.arguments().size())return false;
        Map<String,Object> event=new HashMap<>();event.put("sender",sender);if(platform.isPlayer(sender))event.put("player",sender);
        for(int i=0;i<arguments.size();i++)event.put("arg-"+(i+1),arguments.get(i));
        Task task=new Task(new Context(this,event,new HashMap<>(),50000),command.source());task.frames.push(new BlockFrame(command.body()));run(task);return !task.failed;
    }
    private void execute(List<Statement> body,Context context,Syntax.Source source){Task task=new Task(context,source);task.frames.push(new BlockFrame(body));run(task);}
    private void run(Task task){
        Context c=task.context;
        try{
            while(!task.frames.isEmpty()){
                if(c.generation!=generation){finish(task);return;}
                if(--tickRemaining<0)throw new IllegalStateException("Global script tick budget exceeded");c.consume();
                Frame frame=task.frames.peek();Statement statement=frame.next(c);
                if(statement==null){task.frames.pop();frame.close(c);continue;}
                task.current=statement.source();platform.trace(task.current,statement.getClass().getSimpleName(),c);
                if(statement instanceof Effect effect)effect.action().execute(c);
                else if(statement instanceof Condition condition){
                    Object value=condition.condition().evaluate(c);
                    if(value!=null&&!(value instanceof Boolean))throw new IllegalArgumentException("Standalone condition must evaluate to a boolean");
                    if(!Boolean.TRUE.equals(value))task.frames.pop().close(c);
                }
                else if(statement instanceof Branch branch){for(Arm arm:branch.arms())if(arm.condition()==null||Values.truth(arm.condition().evaluate(c))){task.frames.push(new BlockFrame(arm.body()));break;}}
                else if(statement instanceof Loop loop){Map<String,Object> entries=new LinkedHashMap<>();
                    if(loop.variable()!=null&&loop.kind().equals("value")){String variable=c.text(loop.variable().evaluate(c));entries.putAll(variables.entries(variable,c.locals));}
                    else {int i=1;for(Object value:Values.list(loop.values().evaluate(c)))entries.put(Integer.toString(i++),value);}
                    if(entries.size()>10000)throw new IllegalStateException("List exceeds 10000 loop entries");task.frames.push(new LoopFrame(new ArrayList<>(entries.entrySet()),loop.kind(),loop.body(),c));
                }else if(statement instanceof While loop)task.frames.push(new WhileFrame(loop));
                else if(statement instanceof Wait wait){
                    Object value=wait.duration().evaluate(c);if(!(value instanceof Values.Span span)||!Double.isFinite(span.milliseconds())||span.milliseconds()<0||span.milliseconds()>31_536_000_000d)throw new IllegalArgumentException("Invalid wait duration");
                    if(scheduled.size()>=4096)throw new IllegalStateException("Scheduled script task limit exceeded");c.suspendTransaction();c.delayed=true;task.at=tick+span.ticks();task.order=sequence++;scheduled.add(task);return;
                }else if(statement instanceof Return value){task.result=value.value().evaluate(c);finish(task);return;}
                else if(statement instanceof Stop){finish(task);return;}
                else if(statement instanceof Exit||statement instanceof Continue){
                    while(!task.frames.isEmpty()&&!(task.frames.peek() instanceof LoopFrame)&&!(task.frames.peek() instanceof WhileFrame)){Frame drop=task.frames.pop();drop.close(c);}
                    if(task.frames.isEmpty())throw new IllegalStateException("Loop control used outside loop");if(statement instanceof Exit)task.frames.pop().close(c);
                }
            }
            if(c.depth==0||task.detached)c.commit();
        }catch(RuntimeException error){task.failed=true;if(c.depth==0||task.detached)try{c.rollback();}catch(RuntimeException rollbackFailure){error.addSuppressed(rollbackFailure);}finish(task);if(c.depth>0&&!task.detached)throw error;platform.error(task.current,error);}
    }
    private void finish(Task task){while(!task.frames.isEmpty())task.frames.pop().close(task.context);if((task.context.depth==0||task.detached)&&!task.failed)task.context.commit();}
    private final class Task {
        final Context context;final Deque<Frame> frames=new ArrayDeque<>();long at,order;Object result;boolean failed,detached;Syntax.Source current;
        Task(Context context,Syntax.Source source){this.context=context;current=source;}
    }
    private interface Frame {Statement next(Context c);default void close(Context c){}}
    private static final class BlockFrame implements Frame {
        private final List<Statement> body;private int index;
        BlockFrame(List<Statement> body){this.body=body;}
        public Statement next(Context c){return index<body.size()?body.get(index++):null;}
    }
    private final class LoopFrame implements Frame {
        private final List<Map.Entry<String,Object>> entries;private final String kind;private final List<Statement> body;private final Map<String,Object> previous;private int index;
        LoopFrame(List<Map.Entry<String,Object>> entries,String kind,List<Statement> body,Context c){this.entries=entries;this.kind=kind;this.body=body;previous=new HashMap<>(c.loops);}
        public Statement next(Context c){
            if(index>=entries.size())return null;Map.Entry<String,Object> entry=entries.get(index++);
            c.loops.clear();c.loops.putAll(previous);
            bind(c,"loop-value",entry.getValue());bind(c,"loop-index",entry.getKey());bind(c,"loop-iteration",(double)index);
            if(kind.equals("number"))bind(c,"loop-number",entry.getValue());
            if(platform.isEntity(entry.getValue()))bind(c,"loop-entity",entry.getValue());
            if(platform.isPlayer(entry.getValue()))bind(c,"loop-player",entry.getValue());
            bind(c,"loop-item",entry.getValue());
            return new Branch(List.of(new Arm(null,body)),new Syntax.Source("<loop>",0,""));
        }
        private void bind(Context c,String key,Object value){int occurrence=1;while(previous.containsKey(key+"-"+occurrence))occurrence++;c.loops.put(key+"-"+occurrence,value);c.loops.put(key,value);}
        public void close(Context c){c.loops.clear();c.loops.putAll(previous);}
    }
    private static final class WhileFrame implements Frame {
        private final While loop;private boolean first=true;WhileFrame(While loop){this.loop=loop;}
        public Statement next(Context c){boolean execute=first&&loop.doFirst()||Values.truth(loop.condition().evaluate(c));first=false;return execute?new Branch(List.of(new Arm(null,loop.body())),loop.source()):null;}
    }
}
