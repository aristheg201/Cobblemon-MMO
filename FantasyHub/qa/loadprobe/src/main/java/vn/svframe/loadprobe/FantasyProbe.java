package vn.svframe.loadprobe;
import java.lang.management.ManagementFactory;import java.util.*;
/** Identical callback instrumentation on baseline and candidate, independent of whole-server timing. */
public final class FantasyProbe {
 private static final com.sun.management.ThreadMXBean THREADS=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 private static final long[] wall=new long[12000],cpu=new long[12000],allocated=new long[12000];private static int count,overflow;private static long start,cpuStart,bytesStart;private static boolean measuring;
 public static void reset(){count=overflow=0;}
 public static void begin(){measuring=LoadProbe.recording;if(measuring){start=System.nanoTime();cpuStart=THREADS.getCurrentThreadCpuTime();bytesStart=THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId());}}
 public static void end(){if(measuring&&LoadProbe.recording){if(count<wall.length){wall[count]=System.nanoTime()-start;cpu[count]=THREADS.getCurrentThreadCpuTime()-cpuStart;allocated[count++]=THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId())-bytesStart;}else overflow++;}}
 public static Map<String,Object> snapshot(){return Map.of("wallNanos",Arrays.copyOf(wall,count),"cpuNanos",Arrays.copyOf(cpu,count),"allocatedBytes",Arrays.copyOf(allocated,count),"overflow",overflow);}
}
