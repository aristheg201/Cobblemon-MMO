import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import jdk.jfr.consumer.*;

/** Streaming cross-check. Reports sampled stacks, not a claim of exhaustive execution tracing. */
class JfrReport {
  static long number(String text,String key) {
    var m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*(\\d+)").matcher(text);
    if(!m.find())throw new IllegalArgumentException(key);return Long.parseLong(m.group(1));
  }
  static final class Window {
    String label;long start,end,mainSamples,temperatureSamples,workerSamples,workerLiveFrames,mainAllocationWeight,workerAllocationWeight,gcPauseNanos,gcPauseMax;
    Window(Path path)throws Exception {label=path.getParent().getFileName().toString();String json=Files.readString(path);start=number(json,"startEpochMs");end=number(json,"endEpochMs");}
  }
  public static void main(String[] args)throws Exception {
    var windows=new ArrayList<Window>();for(int i=1;i<args.length;i++)windows.add(new Window(Path.of(args[i])));
    try(var recording=new RecordingFile(Path.of(args[0]))) {
      while(recording.hasMoreEvents()) {
        var event=recording.readEvent();long at=event.getStartTime().toEpochMilli();String type=event.getEventType().getName();
        if(!Set.of("jdk.ExecutionSample","jdk.ObjectAllocationSample","jdk.GCPhasePause").contains(type))continue;
        for(var w:windows) {
          if(at<w.start||at>w.end)continue;
          if(type.equals("jdk.GCPhasePause")){long pause=event.getDuration().toNanos();w.gcPauseNanos+=pause;w.gcPauseMax=Math.max(w.gcPauseMax,pause);continue;}
          RecordedThread thread=type.equals("jdk.ExecutionSample")?event.getThread("sampledThread"):event.getThread();if(thread==null)continue;
          String name=thread.getJavaName();boolean worker=name.startsWith("fantasyhub-json-"),main=name.equals("Server thread");
          if(type.equals("jdk.ObjectAllocationSample")){long bytes=event.getLong("weight");if(worker)w.workerAllocationWeight+=bytes;if(main)w.mainAllocationWeight+=bytes;continue;}
          if(main)w.mainSamples++;if(worker)w.workerSamples++;
          var stack=event.getStackTrace();if(stack==null)continue;boolean temperature=false,live=false;
          for(var frame:stack.getFrames()) {
            String owner=frame.getMethod().getType().getName();
            temperature|=owner.startsWith("toughasnails.temperature.");
            live|=owner.startsWith("net.minecraft.")||owner.startsWith("vn.svframe.svframemmo.")||owner.startsWith("vn.svframe.svframeitems.")||owner.startsWith("com.cobblemon.");
          }
          if(main&&temperature)w.temperatureSamples++;if(worker&&live)w.workerLiveFrames++;
        }
      }
    }
    for(var w:windows)System.out.printf(Locale.ROOT,"players=%s mainSamples=%d temperatureSamples=%d workerSamples=%d observedWorkerLiveMinecraftFrames=%d mainAllocationSampleWeightBytes=%d workerAllocationSampleWeightBytes=%d gcPauseMs=%.3f maxGcPauseMs=%.3f%n",w.label,w.mainSamples,w.temperatureSamples,w.workerSamples,w.workerLiveFrames,w.mainAllocationWeight,w.workerAllocationWeight,w.gcPauseNanos/1e6,w.gcPauseMax/1e6);
  }
}
