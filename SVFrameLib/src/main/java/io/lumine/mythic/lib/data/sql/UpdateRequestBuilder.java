package io.lumine.mythic.lib.data.sql;
import java.sql.*; import java.util.*;
public class UpdateRequestBuilder<H extends io.lumine.mythic.lib.data.SynchronizedDataHolder> {
    private final SQLDatabase<H,?> database; private final List<Entry> entries=new ArrayList<>(); private boolean executed;
    public UpdateRequestBuilder(SQLDatabase<H,?> database){this.database=Objects.requireNonNull(database);}
    public void appendString(String key,Object value){append(key,value==null?null:String.valueOf(value));} public void appendInt(String k,int v){append(k,v);} public void appendLong(String k,long v){append(k,v);} public void appendDouble(String k,double v){append(k,v);}
    public void appendCollection(String k,Iterable<?> values){ArrayList<Object> l=new ArrayList<>();if(values!=null)values.forEach(l::add);append(k,io.lumine.mythic.lib.MythicLib.plugin.getGson().toJson(l));}
    public void appendObject(String k,Map<?,?> value){append(k,io.lumine.mythic.lib.MythicLib.plugin.getGson().toJson(value));}
    private void append(String k,Object v){if(k==null||k.isBlank())throw new IllegalArgumentException("SQL key cannot be blank");entries.add(new Entry(k,v));}
    public void execute(){if(executed)throw new IllegalStateException("Already executed");executed=true;if(entries.isEmpty())return;
        String cols=entries.stream().map(e->"`"+e.key+"`").collect(java.util.stream.Collectors.joining(","));
        String qs=String.join(",",Collections.nCopies(entries.size(),"?"));
        String updates=entries.stream().skip(1).map(e->"`"+e.key+"`=VALUES(`"+e.key+"`)").collect(java.util.stream.Collectors.joining(","));
        String sql="INSERT INTO `"+database.getUserDataTableName()+"` ("+cols+") VALUES ("+qs+")"+(updates.isEmpty()?"":" ON DUPLICATE KEY UPDATE "+updates);
        try(Connection c=database.getConnection();PreparedStatement s=c.prepareStatement(sql)){for(int i=0;i<entries.size();i++)s.setObject(i+1,entries.get(i).value);s.executeUpdate();}
        catch(SQLException mysql){ // SQLite-compatible fallback
            String sql2="INSERT OR REPLACE INTO `"+database.getUserDataTableName()+"` ("+cols+") VALUES ("+qs+")";
            try(Connection c=database.getConnection();PreparedStatement s=c.prepareStatement(sql2)){for(int i=0;i<entries.size();i++)s.setObject(i+1,entries.get(i).value);s.executeUpdate();}catch(SQLException e){throw new RuntimeException("Could not save player data",e);}
        }
    }
    private record Entry(String key,Object value){}
}
