package io.lumine.mythic.lib.data.yaml;
import io.lumine.mythic.lib.data.*; import io.lumine.mythic.lib.data.queue.DataLoadResult; import io.lumine.mythic.lib.module.MMOPlugin; import io.lumine.mythic.lib.profile.SessionUpdateReason; import io.lumine.mythic.lib.util.config.YamlFile;
import java.nio.file.*; import java.util.*;
public abstract class YAMLFlatDatabase<H extends SynchronizedDataHolder,O extends OfflineDataHolder> implements Database<H,O> {
    private final MMOPlugin owning; private final Path folder;
    public YAMLFlatDatabase(MMOPlugin owning){this.owning=Objects.requireNonNull(owning);this.folder=vn.svframe.mythiclibfabric.MythicLibFabricMod.configRoot().resolve("userdata").resolve(owning.getNamespacedKey());}
    @Override public MMOPlugin getPlugin(){return owning;} @Override public void setup(){try{Files.createDirectories(folder);}catch(Exception e){throw new RuntimeException(e);}} @Override public void close(){}
    @Override public void saveData(H data,SessionUpdateReason reason){Map<String,Object> section=new LinkedHashMap<>();section.put("uuid",data.getEffectiveId().toString());section.put("is_saved",reason==SessionUpdateReason.AUTOSAVE?0:1);saveInSection(data,section);YamlFile f=new YamlFile(owning,"userdata/"+owning.getNamespacedKey(),data.getEffectiveId()+".yml",section);f.save();}
    public abstract void saveInSection(H data,Map<String,Object> section);
    @Override public DataLoadResult loadData(H data,boolean sync){Path p=folder.resolve(data.getEffectiveId()+".yml");if(!Files.exists(p))return new DataLoadResult(true,sync);YamlFile f=new YamlFile(owning,"userdata/"+owning.getNamespacedKey(),data.getEffectiveId()+".yml");return loadFromSection(data,f.getContent(),sync);}
    @Override public void confirmReception(H data){Path p=folder.resolve(data.getEffectiveId()+".yml");if(!Files.exists(p))return;YamlFile f=new YamlFile(owning,"userdata/"+owning.getNamespacedKey(),data.getEffectiveId()+".yml");f.getContent().put("is_saved",0);f.save();}
    protected abstract DataLoadResult loadFromSection(H data,Map<String,Object> section,boolean sync);
    @Override public List<UUID> retrieveAllPlayerIds(){if(!Files.isDirectory(folder))return List.of();try(var s=Files.list(folder)){return s.filter(p->p.getFileName().toString().endsWith(".yml")).map(p->p.getFileName().toString().replace(".yml","")).map(x->{try{return UUID.fromString(x);}catch(Exception e){return null;}}).filter(Objects::nonNull).toList();}catch(Exception e){throw new RuntimeException(e);}}
    @SuppressWarnings("unchecked") @Override public O getOffline(UUID id){return (O)new DefaultOfflineDataHolder(id);}
}
