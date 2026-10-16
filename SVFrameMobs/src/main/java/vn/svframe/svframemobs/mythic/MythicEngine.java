package vn.svframe.svframemobs.mythic;

import java.util.*;

/** Server-thread graph execution. Timers retain immutable target/origin metadata. */
public final class MythicEngine {
    public record Point(String world,double x,double y,double z) {
        public Point {if(world==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Invalid skill location");}
        public Point add(double dx,double dy,double dz){return new Point(world,x+dx,y+dy,z+dz);}
    }
    public record Target(UUID entity,Point point) { }
    public record Context(UUID caster,UUID trigger,Target target,Point origin,Map<String,String> parameters,
                          Map<String,String> variables,Execution execution,int depth) {
        public Context {parameters=Map.copyOf(parameters);}
        public Context target(Target target){return new Context(caster,trigger,target,origin,parameters,variables,execution,depth);}
        public Context at(Point point){return new Context(caster,trigger,target,point,parameters,variables,execution,depth);}
        public Context child(Target target,Map<String,String> overrides) {
            Map<String,String> merged=new LinkedHashMap<>(parameters);merged.putAll(overrides);
            return new Context(caster,trigger,target,target!=null&&target.point()!=null?target.point():origin,merged,variables,execution,depth+1);
        }
    }
    public static final class Execution {
        private int steps;private long budgetTick=Long.MIN_VALUE;private boolean cancelled;
        public boolean cancelled(){return cancelled;}
        public void cancel(){cancelled=true;}
    }
    public static final class Prepared {
        private final MythicDefinition definition;private final Context context;private final long generation;private boolean started;
        private Prepared(MythicDefinition definition,Context context,long generation){this.definition=definition;this.context=context;this.generation=generation;}
    }
    public interface Platform {
        boolean valid(Context context);
        boolean condition(MythicLine.Condition condition,Context context,Target target);
        List<Target> select(MythicLine.Selector selector,Context context);
        double number(String expression,Context context,Target target);
        String text(String expression,Context context,Target target);
        void execute(MythicLine action,Context context,Target target,MythicEngine engine);
        List<String> validate(MythicDefinition definition);
        void failed(String skill,Exception error);
        default boolean externalAvailable(String id){return false;}
    }
    private record Task(long tick,long order,long generation,Context context,Runnable action) { }
    private record Cooldown(UUID actor,String id) { }
    private final Platform platform;
    private final PriorityQueue<Task> scheduled=new PriorityQueue<>(Comparator.comparingLong(Task::tick).thenComparingLong(Task::order));
    private final Map<Cooldown,Long> cooldowns=new HashMap<>();
    private Map<String,MythicDefinition> definitions=Map.of();
    private Map<String,List<String>> failures=Map.of();
    private long tick,order,generation;
    public MythicEngine(Platform platform){this.platform=Objects.requireNonNull(platform);}

    public void replace(Map<String,MythicDefinition> input,Map<String,List<String>> parseFailures) {
        Map<String,List<String>> errors=new LinkedHashMap<>(parseFailures);
        for(var definition:input.values()) {
            List<String> issues=platform.validate(definition);if(!issues.isEmpty())errors.put(definition.id(),List.copyOf(issues));
        }
        // Fixed-point propagation prevents a wrapper from being offered while a
        // nested callback is unavailable. Recursive supported graphs are legal.
        boolean changed;
        do {
            changed=false;
            for(var definition:input.values())if(!errors.containsKey(definition.id())) {
                List<String> problems=new ArrayList<>();
                for(String reference:references(definition)) {
                    if(errors.containsKey(reference))problems.add("Unavailable meta-skill "+reference);
                    else if(!input.containsKey(reference)&&!platform.externalAvailable(reference))problems.add("Missing meta-skill "+reference);
                }
                if(!problems.isEmpty()){errors.put(definition.id(),List.copyOf(problems));changed=true;}
            }
        }while(changed);
        definitions=Map.copyOf(input);failures=Map.copyOf(errors);generation++;scheduled.clear();cooldowns.clear();
    }
    public static Set<String> references(MythicDefinition definition) {
        Set<String> result=new LinkedHashSet<>();
        List<MythicLine.Condition> conditions=new ArrayList<>(definition.conditions());conditions.addAll(definition.targetConditions());conditions.addAll(definition.triggerConditions());
        for(var action:definition.skills()) {
            if(action.mechanic().equals("skill"))result.add(action.value("s",action.value("skill","")));
            if(action.mechanic().equals("randomskill"))for(String item:action.value("skills",action.value("s","")).split(","))result.add(item.trim().split("\\s+")[0]);
            for(String key:List.of("ontick","onhit","onend","onstart"))if(action.parameters().containsKey(key))result.add(action.parameters().get(key));
            if(action.selector()!=null)conditions.addAll(action.selector().conditions());
        }
        for(var condition:conditions)if(!condition.castInstead().isEmpty())result.add(condition.castInstead());
        result.remove("");return result;
    }
    public boolean available(String id){return definitions.containsKey(id)&&!failures.containsKey(id);}
    public boolean defined(String id){return definitions.containsKey(id)||failures.containsKey(id);}
    public Map<String,List<String>> failures(){return failures;}
    public int count(){return definitions.size();}
    public boolean cast(String id,UUID caster,UUID trigger,Target target,Point origin,Map<String,?> parameters) {
        Map<String,String> strings=new LinkedHashMap<>();parameters.forEach((k,v)->strings.put(k,String.valueOf(v)));
        if(target==null)target=new Target(caster,null);
        return call(id,new Context(caster,trigger,target,origin,strings,new LinkedHashMap<>(),new Execution(),0));
    }
    public boolean call(String id,Context context) {
        Prepared prepared=prepare(id,context);return prepared!=null&&start(prepared);
    }
    public Prepared prepare(String id,UUID caster,UUID trigger,Target target,Point origin,Map<String,?> parameters) {
        Map<String,String> strings=new LinkedHashMap<>();parameters.forEach((k,v)->strings.put(k,String.valueOf(v)));
        return prepare(id,new Context(caster,trigger,target==null?new Target(caster,null):target,origin,strings,new LinkedHashMap<>(),new Execution(),0));
    }
    private Prepared prepare(String id,Context context) {
        if(!available(id)||context.depth()>16||context.execution().cancelled()||!platform.valid(context))return null;
        MythicDefinition definition=definitions.get(id);
        Cooldown key=new Cooldown(context.caster(),id);
        if(cooldowns.getOrDefault(key,0L)>tick)return null;
        try {
            for(var condition:definition.conditions()) {
                boolean value=platform.condition(condition,context,new Target(context.caster(),null));
                if(!condition.castInstead().isEmpty()) {
                    if(value)return prepare(condition.castInstead(),context.child(context.target(),Map.of()));
                }else if(value!=condition.expected())return null;
            }
            if(!passes(definition.targetConditions(),context,context.target()))return null;
            if(!passes(definition.triggerConditions(),context,new Target(context.trigger(),null)))return null;
            return new Prepared(definition,context,generation);
        }catch(RuntimeException error){context.execution().cancel();platform.failed(id,error);return null;}
    }
    public boolean start(Prepared prepared) {
        if(prepared==null||prepared.started||prepared.generation!=generation||!platform.valid(prepared.context))return false;
        Cooldown key=new Cooldown(prepared.context.caster(),prepared.definition.id());
        if(cooldowns.getOrDefault(key,0L)>tick)return false;
        prepared.started=true;
        if(prepared.definition.cooldown()>0)cooldowns.put(key,tick+(long)Math.ceil(prepared.definition.cooldown()*20));
        try{run(prepared.definition,prepared.context,0);return true;}catch(RuntimeException error){prepared.context.execution().cancel();platform.failed(prepared.definition.id(),error);return false;}
    }
    private boolean passes(List<MythicLine.Condition> list,Context context,Target target) {
        for(var condition:list)if(platform.condition(condition,context,target)!=condition.expected())return false;return true;
    }
    private boolean consume(Execution execution) {
        if(execution.budgetTick!=tick){execution.budgetTick=tick;execution.steps=0;}
        return ++execution.steps<=4096;
    }
    private void run(MythicDefinition definition,Context context,int index) {
        if(context.execution().cancelled()||!platform.valid(context))return;
        for(int i=index;i<definition.skills().size();i++) {
            if(!consume(context.execution())){context.execution().cancel();throw new IllegalStateException("Skill execution exceeded 4096 steps in one tick");}
            MythicLine action=definition.skills().get(i);
            if(action.mechanic().equals("delay")) {
                int delay=ticks(platform.number(action.value("ticks",action.value("t","0")),context,context.target()),0,72000);int next=i+1;
                if(delay>0){schedule(delay,context,()->safe(definition.id(),context,()->run(definition,context,next)));return;}
                continue;
            }
            if(Math.random()>=action.chance())continue;
            int repeats=1+ticks(platform.number(action.value("repeat","0"),context,context.target()),0,127);
            int period=ticks(platform.number(action.value("repeatinterval","1"),context,context.target()),1,72000);
            int delay=ticks(platform.number(action.value("delay","0"),context,context.target()),0,72000);
            for(int repeat=0;repeat<repeats;repeat++) {
                int after=delay+repeat*period;
                if(after==0)applyToTargets(action,context);else schedule(after,context,()->safe(definition.id(),context,()->applyToTargets(action,context)));
            }
        }
    }
    private void applyToTargets(MythicLine action,Context context) {
        List<Target> selected=action.selector()==null?List.of(context.target()):platform.select(action.selector(),context);
        int limit=action.selector()!=null&&action.selector().name().equals("nearestplayer")?1:
            ticks(platform.number(action.selector()==null?"128":action.selector().parameters().getOrDefault("limit","128"),context,context.target()),1,128);
        int accepted=0;
        for(Target target:selected) {
            if(action.selector()!=null&&!passes(action.selector().conditions(),context,target))continue;
            perform(action,context.target(target),target);
            if(++accepted>=limit)break;
        }
    }
    private void perform(MythicLine action,Context context,Target target) {
        if(context.execution().cancelled()||!platform.valid(context))return;
        if(!consume(context.execution())){context.execution().cancel();throw new IllegalStateException("Skill execution exceeded 4096 effects in one tick");}
        if(action.mechanic().equals("skill")) {
            Map<String,String> overrides=new LinkedHashMap<>();action.parameters().forEach((k,v)->{if(!Set.of("s","skill","repeat","repeatinterval","delay").contains(k))overrides.put(k,platform.text(v,context,target));});
            String id=action.value("s",action.value("skill",""));
            if(definitions.containsKey(id))call(id,context.child(target,overrides));else platform.execute(action,context.child(target,overrides),target,this);
        }else platform.execute(action,context,target,this);
    }
    public void schedule(int delay,Context context,Runnable action) {
        if(scheduled.size()>=16384){context.execution().cancel();throw new IllegalStateException("Skill scheduler capacity exceeded");}
        scheduled.add(new Task(tick+Math.max(1,delay),order++,generation,context,action));
    }
    public void tick() {
        tick++;int remaining=4096;
        while(remaining-->0&&!scheduled.isEmpty()&&scheduled.peek().tick()<=tick) {
            Task task=scheduled.remove();if(task.generation()==generation&&platform.valid(task.context())&&!task.context().execution().cancelled())safe("scheduled",task.context(),task.action());
        }
        if(tick%200==0)cooldowns.entrySet().removeIf(row->row.getValue()<=tick);
    }
    private void safe(String id,Context context,Runnable task) {try{task.run();}catch(RuntimeException failure){context.execution().cancel();platform.failed(id,failure);}}
    public void clear(){generation++;scheduled.clear();cooldowns.clear();}
    public long currentTick(){return tick;}
    public int scheduledCount(){return scheduled.size();}
    public static int ticks(double value,int min,int max){if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException("Invalid timer/count "+value);return (int)Math.ceil(value);}
}
