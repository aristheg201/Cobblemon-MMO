package vn.svframe.worldboss;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
/** Explicit HH:mm:ss slots use second precision; legacy HH:mm slots use the current minute. */
public final class BossSchedule {
 private record Slot(LocalTime time,boolean precise){long window(){return precise?1:60;}}
 private final ZoneId zone;private final List<Slot> slots;
 public BossSchedule(String zone,List<String> times){
  this.zone=ZoneId.of(zone);var list=new ArrayList<Slot>();var seen=new HashSet<LocalTime>();
  for(String text:times){if(text==null||!text.matches("[0-2][0-9]:[0-5][0-9](:[0-5][0-9])?"))throw new IllegalArgumentException("Schedule time must be HH:mm or HH:mm:ss");var time=LocalTime.parse(text);if(!seen.add(time))throw new IllegalArgumentException("Duplicate schedule time "+text);list.add(new Slot(time,text.length()==8));}
  if(list.isEmpty())throw new IllegalArgumentException("Empty schedule");list.sort(Comparator.comparing(Slot::time));slots=List.copyOf(list);
 }
 public List<String> due(Instant now,Set<String> done){
  var at=now.atZone(zone);return slots.stream().filter(slot->slot.time.equals(at.toLocalTime().truncatedTo(slot.precise?ChronoUnit.SECONDS:ChronoUnit.MINUTES))).map(slot->at.toLocalDate().atTime(slot.time).atZone(zone).toString()).filter(id->!done.contains(id)).toList();
 }
 public ZonedDateTime next(Instant now){var at=now.atZone(zone);for(var entry:slots){var slot=at.toLocalDate().atTime(entry.time).atZone(zone);if(slot.plusSeconds(entry.window()).isAfter(at))return slot;}return at.toLocalDate().plusDays(1).atTime(slots.getFirst().time).atZone(zone);}
 public List<ZonedDateTime> upcoming(Instant now,int seconds){var at=now.atZone(zone);var out=new ArrayList<ZonedDateTime>();for(int day=0;day<2;day++)for(var entry:slots){var slot=at.toLocalDate().plusDays(day).atTime(entry.time).atZone(zone);long delta=Duration.between(now,slot).toSeconds();if(delta>0&&delta<=seconds)out.add(slot);}return out;}
}
