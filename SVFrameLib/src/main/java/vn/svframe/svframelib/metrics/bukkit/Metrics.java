package vn.svframe.svframelib.metrics.bukkit;
import vn.svframe.svframelib.module.MMOPlugin; import java.util.*; import java.util.concurrent.Callable;
public class Metrics {
    public static final int B_STATS_VERSION=1; private final MMOPlugin plugin; private final List<CustomChart> charts=new ArrayList<>(); private volatile boolean enabled;
    public Metrics(MMOPlugin plugin){this.plugin=Objects.requireNonNull(plugin);this.enabled=Boolean.getBoolean("svframelib.metrics.enabled");}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean enabled){this.enabled=enabled;} public void addCustomChart(CustomChart chart){if(chart!=null)charts.add(chart);} public List<CustomChart> getCharts(){return List.copyOf(charts);}
    public vn.svframe.svframelib.gson.JsonObject getPluginData(){vn.svframe.svframelib.gson.JsonObject out=new vn.svframe.svframelib.gson.JsonObject();out.addProperty("pluginName",plugin.getNamespacedKey());out.addProperty("enabled",enabled);vn.svframe.svframelib.gson.JsonArray data=new vn.svframe.svframelib.gson.JsonArray();for(CustomChart chart:charts){try{Object value=chart.getChartData();if(value==null)continue;vn.svframe.svframelib.gson.JsonObject c=new vn.svframe.svframelib.gson.JsonObject();c.addProperty("chartId",chart.chartId);c.addProperty("value",String.valueOf(value));data.add(c);}catch(Exception ignored){}}out.add("customCharts",data);return out;}
    public abstract static class CustomChart {final String chartId;CustomChart(String id){chartId=Objects.requireNonNull(id);}public String getChartId(){return chartId;}protected abstract Object getChartData() throws Exception;}
    public static class SingleLineChart extends CustomChart {private final Callable<Integer> callable;public SingleLineChart(String id,Callable<Integer> c){super(id);callable=c;}protected Object getChartData()throws Exception{return callable.call();}}
    public static class SimplePie extends CustomChart {private final Callable<String> callable;public SimplePie(String id,Callable<String> c){super(id);callable=c;}protected Object getChartData()throws Exception{return callable.call();}}
}
