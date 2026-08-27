package vn.svframe.svframelib.data.sql;
import vn.svframe.svframelib.data.*; import vn.svframe.svframelib.data.queue.DataLoadResult; import vn.svframe.svframelib.module.MMOPlugin; import vn.svframe.svframelib.profile.SessionUpdateReason;
import java.sql.*; import java.util.*; import java.util.concurrent.*; import java.util.function.*;
public abstract class SQLDatabase<H extends SynchronizedDataHolder,O extends OfflineDataHolder> implements Database<H,O> {
    private final MMOPlugin plugin; protected final String userdataTableName; protected final String uuidFieldName="uuid"; protected final String databaseName; private volatile String jdbcUrl;
    public SQLDatabase(MMOPlugin plugin,String databaseName){this.plugin=Objects.requireNonNull(plugin);this.databaseName=Objects.requireNonNullElse(databaseName,plugin.getNamespacedKey());this.userdataTableName=plugin.getNamespacedKey().replace('-','_')+"_userdata";this.jdbcUrl=System.getProperty("svframelib.jdbc."+plugin.getNamespacedKey(),"jdbc:sqlite:"+vn.svframe.svframelib.fabric.SVFrameLibFabricMod.configRoot().resolve(this.databaseName+".db"));}
    public void setJdbcUrl(String url){this.jdbcUrl=Objects.requireNonNull(url);}
    public Connection getConnection() throws SQLException{return DriverManager.getConnection(jdbcUrl);}
    @Override public void setup(){try{setupSQL();}catch(SQLException e){throw new RuntimeException("Could not setup SQL database "+databaseName,e);}}
    @Override public void close(){}
    public String getUserDataTableName(){return userdataTableName;} public String getDatabaseName(){return databaseName;} @Override public MMOPlugin getPlugin(){return plugin;}
    private static PreparedStatement prepareStatement(Connection c,String q,String...params)throws SQLException{PreparedStatement s=c.prepareStatement(q);for(int i=0;i<params.length;i++)s.setString(i+1,params[i]);return s;}
    public void executeQuery(String query,SQLConsumer consumer,String...params)throws SQLException{try(Connection c=getConnection();PreparedStatement s=prepareStatement(c,query,params);ResultSet r=s.executeQuery()){consumer.accept(r);}}
    public void executeUpdate(String query,String...params)throws SQLException{try(Connection c=getConnection();PreparedStatement s=prepareStatement(c,query,params)){s.executeUpdate();}}
    @Override public DataLoadResult loadData(H data,boolean sync){String q="SELECT * FROM `"+userdataTableName+"` WHERE `"+uuidFieldName+"`=?";try(Connection c=getConnection();PreparedStatement s=prepareStatement(c,q,data.getEffectiveId().toString());ResultSet r=s.executeQuery()){return r.next()?loadDataFromResultSet(data,r,sync):new DataLoadResult(true,sync);}catch(SQLException e){throw new RuntimeException("Could not load player data",e);}}
    protected abstract void setupSQL() throws SQLException; protected abstract DataLoadResult loadDataFromResultSet(H data,ResultSet result,boolean sync)throws SQLException;
    @Override public void saveData(H data,SessionUpdateReason reason){UpdateRequestBuilder<H> b=new UpdateRequestBuilder<>(this);b.appendString(uuidFieldName,data.getEffectiveId());b.appendInt("is_saved",reason==SessionUpdateReason.AUTOSAVE?0:1);setupSaveRequest(data,b);b.execute();}
    protected abstract void setupSaveRequest(H data,UpdateRequestBuilder<H> builder);
    @Override public void confirmReception(H data){try{executeUpdate("UPDATE `"+userdataTableName+"` SET `is_saved`=0 WHERE `"+uuidFieldName+"`=?",data.getEffectiveId().toString());}catch(SQLException e){plugin.logger().warning("Could not confirm data reception: "+e.getMessage());}}
    @Override public List<UUID> retrieveAllPlayerIds(){ArrayList<UUID> out=new ArrayList<>();try{executeQuery("SELECT `"+uuidFieldName+"` FROM `"+userdataTableName+"`",r->{while(r.next())try{out.add(UUID.fromString(r.getString(1)));}catch(IllegalArgumentException ignored){}});}catch(SQLException e){throw new RuntimeException(e);}return out;}
    @SuppressWarnings("unchecked") @Override public O getOffline(UUID id){return (O)new DefaultOfflineDataHolder(id);}
    public CompletableFuture<Void> executeQueryAsync(String q,Consumer<ResultSet> c,String...p){return CompletableFuture.runAsync(()->{try{executeQuery(q,c::accept,p);}catch(SQLException e){throw new CompletionException(e);}});}
    public CompletableFuture<Void> executeUpdateAsync(String q,String...p){return CompletableFuture.runAsync(()->{try{executeUpdate(q,p);}catch(SQLException e){throw new CompletionException(e);}});}
    public void getResult(String q,Consumer<ResultSet> c){try{executeQuery(q,c::accept);}catch(SQLException e){throw new RuntimeException(e);}}
    public CompletableFuture<Void> getResultAsync(String q,Consumer<ResultSet> c){return executeQueryAsync(q,c);}
    public void execute(Consumer<Connection> c){try(Connection x=getConnection()){c.accept(x);}catch(SQLException e){throw new RuntimeException(e);}}
    public CompletableFuture<Void> executeAsync(Consumer<Connection> c){return CompletableFuture.runAsync(()->execute(c));}
}
