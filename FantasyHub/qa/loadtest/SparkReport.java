import java.nio.file.*;
import java.util.*;
import me.lucko.spark.proto.SparkSamplerProtos;

/** Compile/run with the exact Spark JAR on the classpath. Does not export server configuration. */
class SparkReport {
  public static void main(String[] args)throws Exception {
    var data=SparkSamplerProtos.SamplerData.parseFrom(Files.readAllBytes(Path.of(args[0])));
    var meta=data.getMetadata();var values=new TreeMap<String,Double>();double threadTotal=0;
    for(var thread:data.getThreadsList())if(thread.getName().equals("Server thread")) {
      threadTotal+=thread.getTimesList().stream().mapToDouble(Double::doubleValue).sum();
      for(var node:thread.getChildrenList())values.merge(node.getClassName()+"."+node.getMethodName(),node.getTimesList().stream().mapToDouble(Double::doubleValue).sum(),Double::sum);
    }
    var output=new StringBuilder("{");
    output.append("\"mode\":\"").append(meta.getSamplerMode()).append("\",\"ticks\":").append(meta.getNumberOfTicks());
    output.append(",\"durationMs\":").append(meta.getEndTime()-meta.getStartTime()).append(",\"serverThreadSamples\":").append(threadTotal);
    output.append(",\"players\":").append(meta.getPlatformStatistics().getPlayerCount());
    output.append(",\"tpsLast1m\":").append(meta.getPlatformStatistics().getTps().getLast1M());
    output.append(",\"methods\":{");boolean first=true;
    for(var entry:values.entrySet())if(entry.getKey().equals("net.minecraft.server.MinecraftServer.method_3748")||entry.getKey().contains("fantasyhub.core.NativeSyncRuntime.tick")||entry.getKey().contains("fantasyhub.core.FantasyHubCore.lambda$onInitialize$15")||entry.getKey().contains("fantasyhub.protocol.BeltEquipment.equipped")||entry.getKey().contains("PokemonSkillShopService.catalog")||entry.getKey().contains("toughasnails.temperature.AreaFill.fill")) {
      if(!first)output.append(',');first=false;output.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
    }
    output.append("},\"windows\":[");first=true;
    for(var window:data.getTimeWindowStatisticsMap().values()) {
      if(!first)output.append(',');first=false;output.append("{\"players\":").append(window.getPlayers()).append(",\"chunks\":").append(window.getChunks()).append(",\"entities\":").append(window.getEntities()).append('}');
    }
    System.out.println(output.append("]}"));
  }
}
