package vn.svframe.worldboss;

import com.google.gson.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.io.*;

/** Validated configuration snapshot. Candidate snapshots install only after all adapters validate. */
public final class BossConfig {
 private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
 public static final class Phase {
  public double healthBelow,cooldownMultiplier=1,damageMultiplier=1;
  public List<String> skillIds=new ArrayList<>();
  public Phase(){}
  public Phase(double threshold,double cooldown,double damage){healthBelow=threshold;cooldownMultiplier=cooldown;damageMultiplier=damage;}
 }
 public static final class Boss {
  public String id,name,type,properties,classId;
  public double damageMitigation=0,stoneDropChance=.15,gauntletDropChance=0;
  public Map<String,Double> mechanics=new LinkedHashMap<>();
  public String bossBarColor="RED";public List<String> stoneDrops=new ArrayList<>();
  public double health=1000,scale=2,cooldownMultiplier=1,damageMultiplier=1;
  public int attackSeconds=5,windupSeconds=2;
  public String telegraphColor="FF7043";
  public Integer winReward,lossReward;
  public List<String> skillIds=new ArrayList<>();
  public List<Phase> phases=new ArrayList<>(List.of(new Phase(.6,.85,1.15),new Phase(.25,.7,1.3)));
  public Boss(){}
  public Boss(String id,String name,String type,String value){this.id=id;this.name=name;this.type=type;if(type.equals("pokemon"))properties=value;else classId=value;}
  public int phase(double healthRatio){int result=1;for(var p:phases)if(healthRatio<=p.healthBelow)result++;return result;}
  private Phase active(double ratio){int index=phase(ratio)-2;return index<0?null:phases.get(index);}
  public List<String> rotation(double ratio){var p=active(ratio);return p==null||p.skillIds.isEmpty()?skillIds:p.skillIds;}
  public double cooldown(double ratio){var p=active(ratio);return cooldownMultiplier*(p==null?1:p.cooldownMultiplier);}
  public double damage(double ratio){var p=active(ratio);return damageMultiplier*(p==null?1:p.damageMultiplier);}
  public int win(Rewards defaults){return winReward==null?defaults.win:winReward;}
  public int loss(Rewards defaults){return lossReward==null?defaults.loss:lossReward;}
 }
 public static final class Arena {
  public String id,dimension;public boolean personal=false;public double x,y,z,radius=32;
  public Arena(){}
  public Arena(String id,String dimension,double x,double y,double z,double radius){this.id=id;this.dimension=dimension;this.x=x;this.y=y;this.z=z;this.radius=radius;}
 }
 public static final class Bosses {
  public List<Boss> bosses=defaults();
  private static List<Boss> defaults(){
   var pokemon=new Boss("charizard","Charizard — Inferno Lord","pokemon","charizard level=80 uncatchable=true unbattleable=true");
   var thor=new Boss("thor","Thor — God of Thunder","class","THOR");thor.scale=1;thor.skillIds=new ArrayList<>(List.of("MJOLNIR_THROW","THUNDEROUS_SLAM","BIFROST"));
   var spider=new Boss("web_guardian","Spider-Man — Web Guardian","class","SPIDER_MAN");spider.health=800;spider.scale=1;spider.skillIds=new ArrayList<>(List.of("WEB_SHOOTER_BARRAGE","WEB_BLITZ","COCOON_SLAM"));spider.cooldownMultiplier=1.2;spider.damageMultiplier=.8;spider.winReward=18;spider.lossReward=4;
   var webPhase=new Phase(.5,.65,1.4);webPhase.skillIds=new ArrayList<>(List.of("WORLD_WEB_TRAP","AIR_COMBO","WEB_BLITZ"));spider.phases=new ArrayList<>(List.of(webPhase));
   var list=new ArrayList<>(List.of(pokemon,thor,spider));for(var b:list){b.stoneDropChance=.15;b.stoneDrops=new ArrayList<>(b.id.equals("thor")?List.of("power"):b.id.equals("web_guardian")?List.of("soul"):List.of("space","power","reality","mind","soul","time"));}
   var extra=new com.google.gson.Gson().fromJson("""
[{"id": "thanos", "name": "§5§lThanos, The Mad Titan", "type": "cosmic", "classId": "THANOS", "health": 10000, "scale": 1.65, "damageMitigation": 0.85, "bossBarColor": "PURPLE", "stoneDrops": ["space", "power", "reality", "mind", "soul", "time"], "stoneDropChance": 0.15, "gauntletDropChance": 0.2, "windupSeconds": 2, "attackSeconds": 5, "telegraphColor": "A050FF", "phases": [{"healthBelow": 0.5, "cooldownMultiplier": 0.8, "damageMultiplier": 1.2}], "mechanics": {"punchDamage": 45, "beamDamage": 6, "meteorDamage": 30, "sweepDamage": 26}}, {"id": "collector", "name": "The Collector", "type": "cosmic", "classId": "COLLECTOR", "health": 8000, "scale": 1.25, "damageMitigation": 0.65, "stoneDropChance": 0.15, "stoneDrops": ["reality"], "bossBarColor": "PURPLE", "windupSeconds": 2, "attackSeconds": 5, "telegraphColor": "DD66FF", "phases": [{"healthBelow": 0.85}, {"healthBelow": 0.6}, {"healthBelow": 0.3}]}, {"id": "speedster", "name": "Speedster — Space Guardian", "type": "class", "classId": "FLASH", "health": 4000, "scale": 1, "stoneDropChance": 0.15, "stoneDrops": ["space"], "skillIds": [], "phases": []}, {"id": "sorcerer_guardian", "name": "Sorcerer Supreme — Guardian of Time and Mind", "type": "class", "classId": "SORCERER_SUPREME", "health": 6000, "scale": 1, "stoneDropChance": 0.15, "stoneDrops": ["mind", "time"], "skillIds": [], "phases": []}, {"id": "ultimate_trial", "name": "All Classes — The Ultimate Test", "type": "cosmic", "classId": "GAUNTLET_TRIAL", "health": 10000, "scale": 1.3, "damageMitigation": 0.7, "stoneDropChance": 0.15, "stoneDrops": ["soul"], "bossBarColor": "PURPLE", "windupSeconds": 2, "attackSeconds": 4, "phases": []}]
""",Boss[].class);list.addAll(List.of(extra));for(var b:list)b.damageMultiplier=java.math.BigDecimal.valueOf(b.damageMultiplier).multiply(java.math.BigDecimal.valueOf(1.15)).doubleValue();return list;
  }
 }
 public static final class Locations {public List<Arena> locations=new ArrayList<>();}
 public static final class ScheduleEntry {public String time;public List<String> bossIds=new ArrayList<>(),arenaIds=new ArrayList<>();}
 public static final class Schedule {
  public String timezone="Asia/Ho_Chi_Minh";
  public List<String> times=new ArrayList<>(List.of("12:00","18:00","21:00"));
  public List<ScheduleEntry> entries=new ArrayList<>();
  public int setupSeconds=60,fightSeconds=600;
  public List<Integer> warningsSeconds=List.of(300,60);
 }
 public static final class Rewards {public int win=12,loss=3;public double minimumDamageFraction=.005;public int minimumParticipationSeconds=30;}
 public record Assignment(String boss,String arena){}
 public static final class Manager {
  private final Path directory;private final BiConsumer<String,List<String>> actorValidator;private final java.util.function.Consumer<String> pokemonValidator;private BossConfig current;
  public Manager(Path directory,BiConsumer<String,List<String>> validator)throws IOException{this(directory,validator,properties->{});}
  public Manager(Path directory,BiConsumer<String,List<String>> validator,java.util.function.Consumer<String> pokemon)throws IOException{this.directory=directory;actorValidator=validator;pokemonValidator=pokemon;reload();}
  public BossConfig current(){return current;}
  public BossConfig reload()throws IOException{var candidate=new BossConfig(directory);candidate.validateActors(actorValidator);for(var boss:candidate.bosses.bosses)if(boss.type.equals("pokemon"))try{pokemonValidator.accept(boss.properties);}catch(RuntimeException ex){throw invalid("bosses["+boss.id+"].properties",ex.getMessage());}current=candidate;return candidate;}
 }
 public Bosses bosses;public Locations locations;public Schedule schedule;public Rewards rewards;private final Path directory;
 public BossConfig(Path directory)throws IOException{
  this.directory=directory;
  bosses=read("bosses.json",Bosses.class,new Bosses());locations=read("locations.json",Locations.class,new Locations());
  schedule=read("schedule.json",Schedule.class,new Schedule());rewards=read("rewards.json",Rewards.class,new Rewards());validate();freeze();
 }
 private <T>T read(String name,Class<T> type,T fallback)throws IOException{
  var file=directory.resolve(name);if(!Files.exists(file)){BossLedger.atomic(file,JSON.toJson(fallback));return fallback;}
  try{var result=JSON.fromJson(Files.readString(file),type);if(result==null)throw new IOException(name+": empty document");return result;}
  catch(JsonParseException ex){throw new IOException(name+": invalid JSON: "+ex.getMessage(),ex);}
 }
 public void saveLocations()throws IOException{BossLedger.atomic(directory.resolve("locations.json"),JSON.toJson(locations));}
 public Boss boss(String id){return bosses.bosses.stream().filter(b->b.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown boss: "+id));}
 public Arena arena(String id){return locations.locations.stream().filter(a->a.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown arena: "+id));}
 public List<String> scheduleTimes(){return schedule.entries.isEmpty()?schedule.times:schedule.entries.stream().map(e->e.time).distinct().sorted().toList();}
 /** Each entry rotates only within its boss IDs; one assignment per arena, in config order. */
 public List<Assignment> assignments(String time,long rotation){
  var out=new LinkedHashMap<String,Assignment>();
  if(schedule.entries.isEmpty()){
   String boss=bosses.bosses.get(Math.floorMod(rotation,bosses.bosses.size())).id;
   for(var arena:locations.locations)if(!arena.personal)out.put(arena.id,new Assignment(boss,arena.id));
  }else for(var entry:schedule.entries)if(LocalTime.parse(entry.time).equals(LocalTime.parse(time))){
   var ids=entry.bossIds.isEmpty()?bosses.bosses.stream().map(b->b.id).toList():entry.bossIds;
   var arenas=entry.arenaIds.isEmpty()?locations.locations.stream().filter(a->!a.personal).map(a->a.id).toList():entry.arenaIds;
   String boss=ids.get(Math.floorMod(rotation,ids.size()));for(var arena:arenas)if(!arena(arena).personal)out.put(arena,new Assignment(boss,arena));
  }
  return List.copyOf(out.values());
 }
 public void validateActors(BiConsumer<String,List<String>> validator){
  for(var boss:bosses.bosses)if(boss.type.equals("class")){
   try{validator.accept(boss.classId,boss.skillIds);for(var phase:boss.phases)if(!phase.skillIds.isEmpty())validator.accept(boss.classId,phase.skillIds);}
   catch(RuntimeException ex){throw invalid("bosses["+boss.id+"].classId/skillIds",ex.getMessage());}
  }
 }
 private static IllegalArgumentException invalid(String path,String reason){return new IllegalArgumentException(path+": "+reason);}
 private static void require(boolean condition,String path,String reason){if(!condition)throw invalid(path,reason);}
 private static boolean identifier(String id){return id!=null&&id.matches("[a-z0-9_-]{1,48}");}
 private static boolean between(double n,double min,double max){return Double.isFinite(n)&&n>=min&&n<=max;}
 private static void multiplier(double n,String path){require(between(n,.1,10),path,"must be finite and within 0.1..10");}
 private static void skills(List<String> list,String path){require(list!=null,path,"must be a list");var seen=new HashSet<String>();for(String id:list)require(id!=null&&id.matches("[A-Z][A-Z0-9_]{0,127}")&&seen.add(id),path,"skill IDs must be unique uppercase keys or full IDs");}
 private void validate(){
  require(bosses.bosses!=null&&!bosses.bosses.isEmpty(),"bosses","at least one boss required");
  var ids=new HashSet<String>();
  for(int i=0;i<bosses.bosses.size();i++){
   var b=bosses.bosses.get(i);String p="bosses["+i+"]";require(b!=null,p,"must be an object");
   require(identifier(b.id)&&ids.add(b.id),p+".id","must be a unique lowercase ID");
   require(b.name!=null&&!b.name.isBlank()&&b.name.length()<=160,p+".name","required, at most 160 characters");
   require("pokemon".equals(b.type)||"class".equals(b.type)||"cosmic".equals(b.type),p+".type","must be pokemon, class or cosmic");
   require(between(b.health,1,1_000_000),p+".health","must be 1..1000000");require(between(b.scale,.1,16),p+".scale","must be 0.1..16");
   require(b.attackSeconds>=2&&b.attackSeconds<=3600,p+".attackSeconds","must be 2..3600");
   require(b.windupSeconds>=1&&b.windupSeconds<=30,p+".windupSeconds","must be 1..30 seconds");
   require(b.telegraphColor!=null&&b.telegraphColor.matches("[0-9A-Fa-f]{6}"),p+".telegraphColor","must be six hexadecimal RGB digits");
   multiplier(b.cooldownMultiplier,p+".cooldownMultiplier");multiplier(b.damageMultiplier,p+".damageMultiplier");skills(b.skillIds,p+".skillIds");
   require(b.winReward==null||b.winReward>=1&&b.winReward<=4096,p+".winReward","must be 1..4096");require(b.lossReward==null||b.lossReward>=1&&b.lossReward<=4096,p+".lossReward","must be 1..4096");
   require(b.mechanics!=null&&b.mechanics.values().stream().allMatch(v->v!=null&&between(v,0,10000)),p+".mechanics","values must be finite and 0..10000");require(between(b.damageMitigation,0,.99),p+".damageMitigation","must be 0..0.99");require(between(b.stoneDropChance,0,1)&&between(b.gauntletDropChance,0,1),p+".dropChance","must be 0..1");require(b.stoneDrops!=null&&b.stoneDrops.stream().allMatch(v->java.util.Set.of("space","power","reality","mind","soul","time").contains(v)),p+".stoneDrops","unknown stone");require(java.util.Set.of("RED","PINK","BLUE","GREEN","YELLOW","PURPLE","WHITE").contains(b.bossBarColor),p+".bossBarColor","invalid color");
   if(b.type.equals("cosmic"))require(java.util.Set.of("THANOS","COLLECTOR","GAUNTLET_TRIAL").contains(b.classId),p+".classId","unknown cosmic encounter");
   else if(b.type.equals("class"))require(b.classId!=null&&b.classId.matches("[A-Z][A-Z0-9_]{0,63}"),p+".classId","required uppercase native class ID");
   else {require(b.properties!=null&&!b.properties.isBlank()&&b.properties.length()<=900,p+".properties","required native Pokemon properties, at most 900 characters");require(b.skillIds.isEmpty(),p+".skillIds","class-only; configure Pokemon moves through native properties");}
   require(b.phases!=null&&b.phases.size()<=8,p+".phases","must be a list of at most eight phase transitions");double previous=1;
   for(int n=0;n<b.phases.size();n++){
    var phase=b.phases.get(n);String pp=p+".phases["+n+"]";require(phase!=null,pp,"must be an object");require(between(phase.healthBelow,.01,.99)&&phase.healthBelow<previous,pp+".healthBelow","must be strictly descending within 0.01..0.99");previous=phase.healthBelow;
    multiplier(phase.cooldownMultiplier,pp+".cooldownMultiplier");multiplier(phase.damageMultiplier,pp+".damageMultiplier");skills(phase.skillIds,pp+".skillIds");multiplier(b.cooldownMultiplier*phase.cooldownMultiplier,pp+".cooldownMultiplier combined with boss");multiplier(b.damageMultiplier*phase.damageMultiplier,pp+".damageMultiplier combined with boss");if(b.type.equals("pokemon"))require(phase.skillIds.isEmpty(),pp+".skillIds","class-only rotation");
   }
  }
  require(locations.locations!=null,"locations","must be a list");var arenas=new HashSet<String>();
  for(int i=0;i<locations.locations.size();i++){
   var a=locations.locations.get(i);String p="locations["+i+"]";require(a!=null,p,"must be an object");require(identifier(a.id)&&arenas.add(a.id),p+".id","must be a unique lowercase ID");require(a.dimension!=null&&a.dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),p+".dimension","required namespace:path");require(Double.isFinite(a.x)&&Double.isFinite(a.y)&&Double.isFinite(a.z),p,"coordinates must be finite");require(between(a.radius,8,128),p+".radius","must be 8..128");
  }
  for(int i=0;i<locations.locations.size();i++)for(int j=i+1;j<locations.locations.size();j++){var a=locations.locations.get(i);var b=locations.locations.get(j);if(a.dimension.equals(b.dimension)&&Math.sqrt(Math.pow(a.x-b.x,2)+Math.pow(a.y-b.y,2)+Math.pow(a.z-b.z,2))<a.radius+b.radius+16)throw invalid("locations","arena reservation zones overlap: "+a.id+" / "+b.id);}
  require(schedule.entries!=null,"schedule.entries","must be a list");require(schedule.times!=null,"schedule.times","must be a list");
  for(int i=0;i<schedule.entries.size();i++){
   var e=schedule.entries.get(i);String p="schedule.entries["+i+"]";require(e!=null,p,"must be an object");time(e.time,p+".time");require(e.bossIds!=null&&e.arenaIds!=null,p,"bossIds and arenaIds must be lists");
   for(String id:e.bossIds)require(ids.contains(id),p+".bossIds","unknown boss "+id);for(String id:e.arenaIds)require(arenas.contains(id),p+".arenaIds","unknown arena "+id);
  }
  var times=new HashSet<String>();for(var t:scheduleTimes()){time(t,"schedule.times");require(times.add(LocalTime.parse(t).toString()),"schedule.times","duplicate time "+t);}
  try{new BossSchedule(schedule.timezone,scheduleTimes());}catch(RuntimeException ex){throw invalid("schedule.timezone/times",ex.getMessage());}
  require(schedule.setupSeconds>=0&&schedule.setupSeconds<=86400,"schedule.setupSeconds","must be 0..86400");require(schedule.fightSeconds>=1&&schedule.fightSeconds<=86400,"schedule.fightSeconds","must be 1..86400");require(schedule.warningsSeconds!=null&&schedule.warningsSeconds.stream().allMatch(n->n!=null&&n>0&&n<=86400),"schedule.warningsSeconds","positive seconds up to 86400");
  require(rewards.win>=1&&rewards.win<=4096&&rewards.loss>=1&&rewards.loss<=4096,"rewards.win/loss","must be 1..4096");require(between(rewards.minimumDamageFraction,.000001,1),"rewards.minimumDamageFraction","must be finite and within 0.000001..1");require(rewards.minimumParticipationSeconds>=1,"rewards.minimumParticipationSeconds","must be positive");
 }
 private void freeze(){
  for(var boss:bosses.bosses){boss.skillIds=List.copyOf(boss.skillIds);for(var phase:boss.phases)phase.skillIds=List.copyOf(phase.skillIds);boss.phases=List.copyOf(boss.phases);}
  bosses.bosses=List.copyOf(bosses.bosses);schedule.times=List.copyOf(schedule.times);schedule.warningsSeconds=List.copyOf(schedule.warningsSeconds);
  for(var entry:schedule.entries){entry.bossIds=List.copyOf(entry.bossIds);entry.arenaIds=List.copyOf(entry.arenaIds);}schedule.entries=List.copyOf(schedule.entries);
 }
 private static void time(String value,String path){try{require(value!=null&&value.matches("[0-2][0-9]:[0-5][0-9](:[0-5][0-9])?"),path,"must be HH:mm or HH:mm:ss");LocalTime.parse(value);}catch(java.time.DateTimeException ex){throw invalid(path,"must be valid HH:mm or HH:mm:ss");}}
}
