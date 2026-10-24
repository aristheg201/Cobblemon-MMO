package vn.svframe.fantasyhub.core;
import java.util.*;import java.lang.reflect.*;import net.minecraft.server.network.ServerPlayerEntity;import vn.svframe.svframelib.skill.PlayerAbilityShop;
/** Optional provider API resolved once. All invocation/capture remains on the server thread. */
final class AbilityCatalogAccess {
 private static Class<?> providerType;private static Method page,revision;
 private static void resolve(PlayerAbilityShop provider){
  if(provider.getClass()==providerType)return;providerType=provider.getClass();page=revision=null;
  try{page=providerType.getMethod("catalogPage",ServerPlayerEntity.class,int.class,int.class);revision=providerType.getMethod("catalogRevision");}
  catch(NoSuchMethodException old){org.slf4j.LoggerFactory.getLogger("FantasyHub/Sync").warn("Ability provider {} has no sliced catalog API; use the supplied Cobblemon Integration 0.1.21 to bound capture work",providerType.getName());}
 }
 static long revision(PlayerAbilityShop provider){if(provider==null)return 0;resolve(provider);try{return revision==null?0:((Number)revision.invoke(provider)).longValue();}catch(ReflectiveOperationException e){throw new IllegalStateException("Ability catalog revision failed",e);}}
 @SuppressWarnings("unchecked") static Map<String,Object> page(PlayerAbilityShop provider,ServerPlayerEntity p,int start,int limit){
  if(provider==null)return Map.of("revision",0L,"total",0,"next",0,"rows",List.of());resolve(provider);
  try{if(page!=null)return (Map<String,Object>)page.invoke(provider,p,start,limit);}
  catch(ReflectiveOperationException e){throw new IllegalStateException("Ability catalog page failed",e);}
  var all=provider.catalog(p);int end=Math.min(all.size(),start+limit);return Map.of("revision",0L,"total",all.size(),"next",end,"rows",all.subList(start,end));
 }
 static void clear(){providerType=null;page=revision=null;}
}
