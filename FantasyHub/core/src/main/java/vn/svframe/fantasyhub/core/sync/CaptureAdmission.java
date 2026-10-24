package vn.svframe.fantasyhub.core.sync;
import java.util.*;
/** Small owner-thread cost model; native calls remain non-preemptible. GC outliers cannot starve work. */
public final class CaptureAdmission {
 private final Map<String,Long> estimates=new HashMap<>();
 public void record(String scope,long nanos){long sample=Math.clamp(nanos,25_000L,10_000_000L);estimates.merge(scope,sample,(old,next)->old+(next-old)/4);}
 public boolean admit(String scope,long elapsed,long budget){long estimated=Math.clamp(estimates.getOrDefault(scope,250_000L)*5/4,50_000L,budget*3/4);return elapsed+estimated<=budget;}
 public void clear(){estimates.clear();}
}
