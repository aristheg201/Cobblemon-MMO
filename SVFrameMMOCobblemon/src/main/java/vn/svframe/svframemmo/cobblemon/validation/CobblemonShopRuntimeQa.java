package vn.svframe.svframemmo.cobblemon.validation;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.move.*;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import java.util.*;
import java.math.*;
import java.nio.file.*;

/** Real configured purchases and owned-player casts; opt-in administrator QA only. */
public final class CobblemonShopRuntimeQa {
    private final ServerPlayerEntity player;
    private final List<PokemonSkillShopService.Offer> offers;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final List<Map<String,Object>> results=new ArrayList<>();
    private int index;
    private CobblemonShopRuntimeQa(ServerPlayerEntity player){this.player=player;offers=SVFrameMMOCobblemon.pokemonSkills().offers(player.getUuid());report.put("offers",results);}
    public static int run(ServerPlayerEntity player){
        if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA")))return 0;
        if(!SVFrameMMOCobblemon.config().pokemonSkills.normalizedProvider().equals("cobbledollars"))throw new IllegalArgumentException("This QA scenario requires the configured real CobbleDollars provider");
        var test=new CobblemonShopRuntimeQa(player);test.next();return 1;
    }
    private BigInteger balance(){try{
        var api=Class.forName("fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt");
        return (BigInteger)api.getMethod("getCobbleDollars",net.minecraft.entity.player.PlayerEntity.class).invoke(null,player);
    }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
    private void next(){
        if(player.isDisconnected()){report.put("failure","player disconnected");save();return;}
        if(index==offers.size()){
            var before=balance();SVFrameMMOCobblemon.pokemonSkills().purchase(player,"flamethrower");
            report.put("unsupported_not_sold",balance().equals(before)&&!SVFrameMMO.externalProgression().isLearned(player.getUuid(),"COBBLEMON_MOVE_FLAMETHROWER"));
            report.put("count",results.size());report.put("finished",true);SVFrameMMO.externalProgression().saveDurably();save();return;
        }
        var offer=offers.get(index++);var entry=new LinkedHashMap<String,Object>();results.add(entry);entry.put("id",offer.moveId());entry.put("price",offer.price().toPlainString());
        BigInteger before=balance();entry.put("balanceBefore",before.toString());entry.put("previouslyOwned",offer.owned());
        SVFrameMMOCobblemon.pokemonSkills().purchase(player,offer.moveId());waitForPurchase(offer,entry,before,0);
    }
    private void waitForPurchase(PokemonSkillShopService.Offer offer,Map<String,Object> entry,BigInteger before,int age){
        SVFrameLibFabricMod.schedule(2,()->{
            if(SVFrameMMOCobblemon.pokemonSkills().isProcessing(player.getUuid())&&age<200){waitForPurchase(offer,entry,before,age+2);return;}
            boolean learned=SVFrameMMO.externalProgression().isLearned(player.getUuid(),offer.skillId());
            BigInteger after=balance(),expected=offer.owned()?before:before.subtract(offer.price().toBigIntegerExact());
            entry.put("balanceAfter",after.toString());entry.put("purchase_correct",learned&&after.equals(expected));
            SVFrameMMOCobblemon.pokemonSkills().purchase(player,offer.moveId());
            entry.put("duplicate_no_charge",balance().equals(after));
            if(!learned){entry.put("failure","purchase did not grant ownership");save();next();return;}
            SVFrameMMO.externalProgression().bind(player.getUuid(),1,offer.skillId());
            entry.put("equipped",offer.skillId().equals(SVFrameMMO.externalProgression().bindings(player.getUuid()).get(1)));
            cast(offer,entry,0);
        });
    }
    private void cast(PokemonSkillShopService.Offer offer,Map<String,Object> entry,int attempt){
        var data=SVFrameMMO.playerData().get(player);var definition=CobblemonMoveSkillAdapter.definition(offer.moveId());
        if(player.getServerWorld().getBlockState(new net.minecraft.util.math.BlockPos(40,64,0)).getCollisionShape(player.getServerWorld(),new net.minecraft.util.math.BlockPos(40,64,0)).isEmpty())throw new IllegalStateException("QA requires a solid test floor at 40,64,0 before testing movement and targeting");
        var cooldown=data.getMMOPlayerData().getCooldownMap();cooldown.resetCooldown(definition.getCooldownPath());cooldown.resetCooldown("svframemmo_global_skill");
        player.networkHandler.requestTeleport(40,65,0,0,10);
        player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);player.fallDistance=0;
        boolean healing=CobblemonMoveProfile.of(com.cobblemon.mod.common.api.moves.Moves.getByName(offer.moveId())).isHeal();
        var cow=healing?null:EntityType.COW.create(player.getServerWorld());
        if(cow!=null){cow.setAiDisabled(true);cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);cow.setHealth(200);cow.refreshPositionAndAngles(40,65,2,0,0);cow.addCommandTag("svframe_qa");player.getServerWorld().spawnEntity(cow);}
        if(healing)player.setHealth(Math.max(1,player.getMaxHealth()-16));
        SVFrameLibFabricMod.schedule(10,()->{
            float healthBefore=player.getHealth();var result=SVFrameMMO.skillRuntime().castBound(data,1);
            boolean effect=healing?player.getHealth()>healthBefore:cow!=null&&cow.getHealth()<200;
            entry.put("castSuccessful",result.isSuccessful());entry.put("attempts",attempt+1);entry.put("actual_effect",effect);
            entry.put("beforeHealth",healing?healthBefore:200);entry.put("afterHealth",healing?player.getHealth():cow==null?0:cow.getHealth());
            if(result.isSuccessful())entry.put("cooldown_rejects_repeat",!SVFrameMMO.skillRuntime().castBound(data,1).isSuccessful());
            if(cow!=null)cow.discard();save();
            // Accuracy misses are legal move behavior. Retry a bounded number of
            // fresh casts; clearing cooldowns here is explicit admin test setup.
            if(!effect&&attempt<5)cast(offer,entry,attempt+1);else SVFrameLibFabricMod.schedule(20,this::next);
        });
    }
    private void save(){try{Path p=Path.of("qa/cobblemon-shop-runtime.json");Files.createDirectories(p.getParent());Files.writeString(p,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
}
