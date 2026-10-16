package vn.svframe.skript.fabric;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.svframelib.api.economy.CurrencyService;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.economy.EconomyRuntime;
import vn.svframe.skript.lang.*;
import vn.svframe.skript.lang.Platform.*;
import vn.svframe.skript.lang.Values.*;

public class FabricPlatform implements Platform {
    private MinecraftServer server;
    final NativeApiBridge api=new NativeApiBridge(this);
    private final Map<UUID,String> names=new HashMap<>();
    public void remember(ServerPlayerEntity player){names.put(player.getUuid(),player.getName().getString());}
    @Override public void requireExecutionThread(){if(server==null||!server.isOnThread())throw new IllegalStateException("Native Skript function execution requires the server thread");}
    @Override public Map<WorldRef,Integer> worldTimes(){
        requireExecutionThread();var out=new LinkedHashMap<WorldRef,Integer>();
        for(ServerWorld world:server.getWorlds())out.put(ref(world),(int)Math.floorMod(world.getTimeOfDay(),24000));return out;
    }
    public void server(MinecraftServer server){this.server=server;names.clear();java.nio.file.Path cache=server.getRunDirectory().resolve("usercache.json");if(java.nio.file.Files.isRegularFile(cache))try{for(var row:com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(cache)).getAsJsonArray()){var json=row.getAsJsonObject();names.put(UUID.fromString(json.get("uuid").getAsString()),json.get("name").getAsString());}}catch(java.io.IOException|RuntimeException e){SVFrameSkript.LOG.warn("Could not read offline profile cache: {}",e.getMessage());}}
    public record InventoryRef(EntityRef owner,String title,int syncId){public InventoryRef(EntityRef owner){this(owner,"Inventory",-1);}}
    public static final class ItemValue implements Values.Copyable {
        public final ItemStack stack;
        public ItemValue(ItemStack stack){this.stack=stack.copy();}
        @Override public Object copy(){return new ItemValue(stack);}
        @Override public boolean equals(Object other){return other instanceof ItemValue item&&ItemStack.areItemsAndComponentsEqual(stack,item.stack)&&stack.getCount()==item.stack.getCount();}
        @Override public int hashCode(){return stack.getItem().hashCode();}
    }
    private static final Set<String> EVENTS=Set.of("load","unload","join","first join","quit","world change","teleport","inventory click","inventory drag","inventory close","command","respawn","death of player","damage","damage of player","right click","left click","class selected","skill cast","character level-up","mana consumed","playermoveevent","playerjumpevent","playerentercastingmodeevent","playerexitcastingmodeevent");
    private static final Set<String> PROPERTIES=Set.of("name","uuid","world","location","eye location","target block","target","gamemode","x-coordinate","y-coordinate","z-coordinate","yaw","pitch","health","max health","level","experience","flight mode","flying state","online","op","inventory","tool","helmet","chestplate","leggings","boots","type","amount","balance","all players","player lookup","offline player lookup","class","mana","stamina","character level");
    private static final Set<String> WRITE=Set.of("location","target","x-coordinate","y-coordinate","z-coordinate","yaw","pitch","health","level","experience","flight mode","flying state","tool","helmet","chestplate","leggings","boots","amount","balance","mana","stamina");
    @Override public boolean supportsEvent(String event){return EVENTS.contains(event);}
    @Override public boolean supportsImport(String name){return api.supportsImport(name);}
    @Override public boolean isType(Object value,String type){return switch(type){case "player"->isPlayer(value);case "living entity"->entity(value) instanceof LivingEntity;case "armor stand"->entity(value) instanceof net.minecraft.entity.decoration.ArmorStandEntity;default->false;};}
    @Override public boolean supportsProperty(String property,boolean writable){return property.equals("time")||(writable?WRITE:PROPERTIES).contains(property);}
    public static EntityRef ref(Entity entity){return new EntityRef(entity.getUuid(),entity instanceof ServerPlayerEntity);}
    public Entity entity(Object value){
        if(value instanceof Entity e)return e;
        if(value instanceof EntityRef ref){if(ref.player())return server.getPlayerManager().getPlayer(ref.id());for(ServerWorld world:server.getWorlds()){Entity e=world.getEntity(ref.id());if(e!=null)return e;}}
        return null;
    }
    public ServerPlayerEntity player(Object value){Entity entity=entity(value);return entity instanceof ServerPlayerEntity p?p:null;}
    public ServerWorld world(WorldRef ref){for(ServerWorld world:server.getWorlds())if(world.getRegistryKey().getValue().toString().equals(ref.id()))return world;return null;}
    public static WorldRef ref(ServerWorld world){return new WorldRef(world.getRegistryKey().getValue().toString());}
    public static Location location(Entity entity){return new Location(new WorldRef(entity.getWorld().getRegistryKey().getValue().toString()),entity.getX(),entity.getY(),entity.getZ(),entity.getYaw(),entity.getPitch());}
    @Override public Object property(String property,Object subject,Context context){
        if(property.equals("all worlds"))return new ArrayList<>(worldTimes().keySet());
        if(property.equals("time")&&subject instanceof Collection<?> worlds)return worlds.stream().map(world->property("time",world,context)).toList();
        if(property.equals("time"))return new GameTime((int)Math.floorMod(timeWorld(subject,context).getTimeOfDay(),24000));
        if(property.equals("all players"))return server.getPlayerManager().getPlayerList().stream().map(FabricPlatform::ref).toList();
        if(property.equals("offline player lookup")){String name=display(subject);ServerPlayerEntity online=server.getPlayerManager().getPlayer(name);if(online!=null)return ref(online);for(var entry:names.entrySet())if(entry.getValue().equalsIgnoreCase(name))return new EntityRef(entry.getKey(),true);return null;}
        if(property.equals("player lookup")){ServerPlayerEntity p=server.getPlayerManager().getPlayer(display(subject));return p==null?null:ref(p);}
        if(subject==null)return null;
        Entity entity=entity(subject);ServerPlayerEntity p=entity instanceof ServerPlayerEntity player?player:null;
        ItemStack item=subject instanceof ItemValue value?value.stack:null;
        Location loc=subject instanceof Location l?l:null;
        return switch(property){
            case "name"->subject instanceof InventoryRef inventory?inventory.title():subject instanceof WorldRef w?worldName(w):entity!=null?entity.getName().getString():item!=null?item.getName().getString():display(subject);
            case "uuid"->entity!=null?entity.getUuid().toString():subject instanceof EntityRef r?r.id().toString():null;
            case "world"->entity!=null?new WorldRef(entity.getWorld().getRegistryKey().getValue().toString()):loc!=null?loc.world():null;
            case "location"->entity!=null?location(entity):loc;
            case "eye location"->entity==null?null:new Location(new WorldRef(entity.getWorld().getRegistryKey().getValue().toString()),entity.getX(),entity.getEyeY(),entity.getZ(),entity.getYaw(),entity.getPitch());
            case "gamemode"->p==null?null:p.interactionManager.getGameMode().getName();
            case "target"->entity instanceof MobEntity mob&&mob.getTarget()!=null?ref(mob.getTarget()):null;
            case "target block"->{if(entity==null)yield null;var hit=entity.raycast(100,1,false);if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK)yield null;var pos=((net.minecraft.util.hit.BlockHitResult)hit).getBlockPos();yield new Location(new WorldRef(entity.getWorld().getRegistryKey().getValue().toString()),pos.getX(),pos.getY(),pos.getZ(),0,0);}
            case "x-coordinate"->loc!=null?loc.x():entity!=null?entity.getX():null;
            case "y-coordinate"->loc!=null?loc.y():entity!=null?entity.getY():null;
            case "z-coordinate"->loc!=null?loc.z():entity!=null?entity.getZ():null;
            case "yaw"->loc!=null?(double)loc.yaw():entity!=null?(double)entity.getYaw():null;
            case "pitch"->loc!=null?(double)loc.pitch():entity!=null?(double)entity.getPitch():null;
            case "health"->entity instanceof LivingEntity living?(double)living.getHealth()/2:null;
            case "max health"->entity instanceof LivingEntity living?(double)living.getMaxHealth()/2:null;
            case "level"->p==null?null:(double)p.experienceLevel;
            case "experience"->p==null?null:(double)p.experienceProgress;
            case "online"->p!=null;
            case "op"->p!=null&&server.getPlayerManager().isOperator(p.getGameProfile());
            case "flight mode"->p==null?null:p.getAbilities().allowFlying;
            case "flying state"->p==null?null:p.getAbilities().flying;
            case "inventory"->p==null?null:new InventoryRef(ref(p));
            case "tool"->p==null?null:new ItemValue(p.getMainHandStack());
            case "helmet","chestplate","leggings","boots"->entity instanceof LivingEntity living?new ItemValue(living.getEquippedStack(slot(property))):null;
            case "type"->item==null?entity==null?null:Registries.ENTITY_TYPE.getId(entity.getType()).getPath():Registries.ITEM.getId(item.getItem()).getPath().replace('_',' ');
            case "amount"->item==null?null:(double)item.getCount();
            case "balance"->p==null?null:CurrencyService.get().balance(p,EconomyRuntime.instance().currency()).doubleValue();
            case "class"->p==null?null:SVFrameMMO.playerData().get(p).getClassId();
            case "character level"->p==null?null:(double)SVFrameMMO.playerData().get(p).getLevel();
            case "mana"->p==null?null:SVFrameMMO.playerData().get(p).getMana();
            case "stamina"->p==null?null:SVFrameMMO.playerData().get(p).getStamina();
            default->throw new IllegalArgumentException("Unsupported property "+property);
        };
    }
    private EquipmentSlot slot(String name){return switch(name){case "helmet"->EquipmentSlot.HEAD;case "chestplate"->EquipmentSlot.CHEST;case "leggings"->EquipmentSlot.LEGS;case "boots"->EquipmentSlot.FEET;default->EquipmentSlot.MAINHAND;};}
    @Override public void changeProperty(String property,Object subject,Object value,String mode,Context context){
        if(!server.isOnThread())throw new IllegalStateException("Script world mutation outside server thread");
        if(property.equals("time")&&subject instanceof Collection<?> worlds){for(Object world:worlds)changeProperty("time",world,value,mode,context);return;}
        if(property.equals("time")){
            ServerWorld world=timeWorld(subject,context);long before=world.getTimeOfDay();
            if(!mode.equals("set")||!(value instanceof GameTime time))throw new IllegalArgumentException("World time requires set to a Minecraft clock time");
            context.rollbackWith(()->world.setTimeOfDay(before));
            world.setTimeOfDay(Math.floorDiv(before,24000)*24000+time.ticks());return;
        }
        if(subject instanceof ItemValue item&&property.equals("amount")){item.stack.setCount(Math.max(0,(int)Values.number(value)));return;}
        ServerPlayerEntity player=player(subject);Entity entity=entity(subject);
        if(entity==null)throw new IllegalArgumentException("Script target is no longer loaded");
        if(property.equals("location")){teleport(subject,value,context);return;}
        if(property.equals("target")){if(entity instanceof MobEntity mob&&entity(value) instanceof LivingEntity target){Entity caster=api.actor(context,mob);if(caster!=null&&vn.svframe.svframelib.entity.RpgEntityAdapters.allows(caster,mob,vn.svframe.svframelib.entity.RpgEntityAdapters.Effect.STATUS)&&vn.svframe.svframelib.entity.RpgEntityAdapters.allows(caster,target,vn.svframe.svframelib.entity.RpgEntityAdapters.Effect.DAMAGE))mob.setTarget(target);}return;}
        if(Set.of("tool","helmet","chestplate","leggings","boots").contains(property)){
            if(!(value instanceof ItemValue item))throw new IllegalArgumentException("Expected item");((LivingEntity)entity).equipStack(slot(property),item.stack.copy());return;
        }
        if(property.equals("flight mode")){requirePlayer(player).getAbilities().allowFlying=Values.truth(value);if(!Values.truth(value))player.getAbilities().flying=false;player.sendAbilitiesUpdate();return;}
        if(property.equals("flying state")){requirePlayer(player).getAbilities().flying=Values.truth(value);player.sendAbilitiesUpdate();return;}
        if(property.equals("balance")){
            var key=EconomyRuntime.instance().currency();var service=CurrencyService.get();BigDecimal amount=BigDecimal.valueOf(Values.number(value));
            var online=requirePlayer(player);BigDecimal before=service.balance(online,key);
            switch(mode){case "set"->{service.setBalance(online,key,amount);context.rollbackWith(()->service.setBalance(online,key,before));}case "add"->{service.deposit(online,key,amount);context.rollbackWith(()->{if(!service.withdraw(online,key,amount))throw new IllegalStateException("Economy rollback rejected");});}case "remove"->{if(!service.withdraw(online,key,amount))throw new IllegalStateException("Economy withdrawal rejected");context.rollbackWith(()->service.deposit(online,key,amount));}default->throw new IllegalArgumentException(mode);}return;
        }
        double old=Values.number(property(property,subject,context));double amount=Values.number(value);double next=switch(mode){case "add"->old+amount;case "remove"->old-amount;case "set"->amount;case "delete"->0;default->throw new IllegalArgumentException(mode);};
        if(!Double.isFinite(next))throw new IllegalArgumentException("Non-finite property value");
        switch(property){
            case "health"->{LivingEntity living=(LivingEntity)entity;api.health(living,next*2,api.actor(context,entity));}
            case "level"->requirePlayer(player).addExperienceLevels((int)next-player.experienceLevel);
            case "experience"->{requirePlayer(player).experienceProgress=(float)Math.max(0,Math.min(1,next));player.totalExperience=0;player.setExperiencePoints((int)(player.experienceProgress*player.getNextLevelExperience()));}
            case "yaw"->entity.setYaw((float)next);case "pitch"->entity.setPitch((float)Math.max(-90,Math.min(90,next)));
            case "mana"->SVFrameMMO.playerData().get(requirePlayer(player)).setMana(next,vn.svframe.svframelib.player.resource.ResourceUpdateReason.OTHER);
            case "stamina"->SVFrameMMO.playerData().get(requirePlayer(player)).setStamina(next,vn.svframe.svframelib.player.resource.ResourceUpdateReason.OTHER);
            default->throw new IllegalArgumentException("Read-only property "+property);
        }
    }
    private ServerWorld timeWorld(Object subject,Context context){
        if(subject instanceof WorldRef ref){ServerWorld world=world(ref);if(world==null)throw new IllegalArgumentException("Script world is not loaded: "+ref.id());return world;}
        if(subject!=null)throw new IllegalArgumentException("Expected world for clock time");
        if(context.event("world") instanceof WorldRef ref)return timeWorld(ref,context);
        ServerPlayerEntity player=player(context.event("player"));return player==null?server.getOverworld():player.getServerWorld();
    }
    private ServerPlayerEntity requirePlayer(ServerPlayerEntity player){if(player==null)throw new IllegalArgumentException("Expected online player");return player;}
    @Override public Object literal(String text){
        if(Set.of("survival","adventure","creative","spectator").contains(text))return text;
        Identifier id=Identifier.tryParse(text.replace(' ','_'));if(id==null||!Registries.ITEM.containsId(id))return null;
        return new ItemValue(Registries.ITEM.get(id).getDefaultStack());
    }
    @Override public String display(Object value){
        if(value instanceof EntityRef ref){Entity e=entity(ref);return e==null?names.getOrDefault(ref.id(),ref.id().toString()):e.getName().getString();}
        if(value instanceof WorldRef world)return worldName(world);
        if(value instanceof ItemValue item)return item.stack.getName().getString();
        if(value instanceof ServerCommandSource source)return source.getName();
        return Values.text(value);
    }
    private String worldName(WorldRef ref){String name=ref.id().substring(ref.id().indexOf(':')+1);return switch(name){case "overworld"->"world";case "the_nether"->"world_nether";case "the_end"->"world_the_end";default->name;};}
    @Override public boolean hasPermission(Object subject,String permission){ServerPlayerEntity player=player(subject);return player!=null?SVFrameMMO.permissions().has(player,permission):subject instanceof ServerCommandSource source&&source.hasPermissionLevel(4);}
    @Override public boolean isPlayer(Object subject){return subject instanceof EntityRef ref&&ref.player()||subject instanceof ServerPlayerEntity;}
    @Override public void send(Object recipient,String message){
        Text text=Text.literal(message.replace('&','§'));for(Object target:Values.list(recipient)){ServerPlayerEntity player=player(target);if(player!=null)player.sendMessage(text,false);else if(target instanceof ServerCommandSource source)source.sendFeedback(()->text,false);}
    }
    @Override public void broadcast(String message){server.getPlayerManager().broadcast(Text.literal(message.replace('&','§')),false);}
    @Override public void command(Object sender,String command){
        command(sender,command,null);
    }
    @Override public void command(Object sender,String command,Context context){
        ServerCommandSource source=sender==null?server.getCommandSource():sender instanceof ServerCommandSource s?s:requirePlayer(player(sender)).getCommandSource();
        String raw=command.replaceFirst("^/","");
        String[] args=raw.split(" +");if(args.length==4&&args[0].equalsIgnoreCase("dm")&&args[1].equalsIgnoreCase("open")){if(!source.hasPermissionLevel(2))throw new IllegalStateException("Menu administration permission required");ServerPlayerEntity target=server.getPlayerManager().getPlayer(args[3]);if(target==null)throw new IllegalArgumentException("Menu recipient is offline");if(!vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.openMenu(args[2],target))throw new IllegalStateException("Native menu integration missing: "+args[2]);return;}
        if(args.length==5&&args[0].equalsIgnoreCase("mmocore")&&args[1].equalsIgnoreCase("admin")&&args[2].equalsIgnoreCase("class")){
            requireExecutionThread();if(!source.hasPermissionLevel(2))throw new IllegalStateException("Class administration permission required");
            ServerPlayerEntity player=server.getPlayerManager().getPlayer(args[3]);if(player==null)throw new IllegalArgumentException("Class recipient is offline");var data=SVFrameMMO.playerData().get(player);var target=SVFrameMMO.classes().getOrThrow(args[4]);
            if(context!=null){var before=vn.svframe.svframemmo.persistence.PlayerDataSnapshot.capture(data);double healthBefore=data.isOnline()?data.getPlayer().getHealth():data.getHealth();context.rollbackWith(()->{data.prepareReload();before.apply(data);data.reloadDefinitions();if(data.isOnline())data.getPlayer().setHealth((float)Math.min(healthBefore,data.getPlayer().getMaxHealth()));SVFrameMMO.playerData().savePlayer(data);});}
            if(!data.changeClass(target,vn.svframe.svframemmo.api.event.PlayerClassChangeEvent.Reason.COMMAND_FORCE))throw new IllegalStateException("Native class command was rejected");
            SVFrameMMO.playerData().savePlayer(data);return;
        }
        // Unknown command integrations are explicit failures, preventing scripts reporting fictitious rewards.
        String root=raw.split(" ",2)[0];if(server.getCommandManager().getDispatcher().getRoot().getChild(root)==null)throw new IllegalStateException("Required command integration is missing: "+root);
        java.util.concurrent.atomic.AtomicReference<Boolean> result=new java.util.concurrent.atomic.AtomicReference<>();
        server.getCommandManager().executeWithPrefix(source.withReturnValueConsumer((success,value)->result.set(success&&value>0)),raw);
        if(Boolean.FALSE.equals(result.get()))throw new IllegalStateException("Native command rejected: "+root);
    }
    @Override public void give(Object recipient,Object value){
        ServerPlayerEntity player=requirePlayer(player(recipient));if(!(value instanceof ItemValue item))throw new IllegalArgumentException("Expected item");
        ItemStack stack=item.stack.copy();player.getInventory().insertStack(stack);if(!stack.isEmpty())player.dropItem(stack,false);
    }
    @Override public void teleport(Object actor,Object destination){
        teleport(actor,destination,null);
    }
    @Override public void teleport(Object actor,Object destination,Context context){
        Entity entity=entity(actor);if(!(destination instanceof Location loc)||entity==null)throw new IllegalArgumentException("Teleport requires a loaded entity and location");ServerWorld world=world(loc.world());if(world==null)throw new IllegalArgumentException("Destination world is missing: "+loc.world().id());
        Entity caster=context==null?entity:api.actor(context,entity);if(caster==null)return;
        if(entity instanceof LivingEntity living&&!vn.svframe.svframelib.entity.RpgEntityAdapters.allows(caster,living,vn.svframe.svframelib.entity.RpgEntityAdapters.Effect.MOVEMENT))return;
        if(entity instanceof ServerPlayerEntity player)player.teleport(world,loc.x(),loc.y(),loc.z(),loc.yaw(),loc.pitch());
        else {if(entity.getWorld()!=world)throw new IllegalArgumentException("Non-player cross-dimension teleport requires entity transfer adapter");entity.refreshPositionAndAngles(loc.x(),loc.y(),loc.z(),loc.yaw(),loc.pitch());}
    }
    @Override public void potion(Object actor,String type,int level,Span duration){
        potion(actor,type,level,duration,null);
    }
    @Override public void potion(Object actor,String type,int level,Span duration,Context context){
        Entity entity=entity(actor);if(!(entity instanceof LivingEntity living))throw new IllegalArgumentException("Potion target is not living");Entity caster=context==null?entity:api.actor(context,entity);if(caster==null||!vn.svframe.svframelib.entity.RpgEntityAdapters.allows(caster,living,vn.svframe.svframelib.entity.RpgEntityAdapters.Effect.STATUS))return;Identifier id=Identifier.tryParse(type.replace(' ','_'));var entry=Registries.STATUS_EFFECT.getEntry(id).orElseThrow(()->new IllegalArgumentException("Unknown potion "+type));living.addStatusEffect(new StatusEffectInstance(entry,(int)Math.min(Integer.MAX_VALUE,duration.ticks()),Math.max(0,level-1)));
    }
    @Override public Expression compileExpression(String s,ExpressionCompiler compiler){
        Expression nativeApi=api.expression(s,compiler);if(nativeApi!=null)return nativeApi;
        if(s.equalsIgnoreCase("console"))return c->server.getCommandSource();
        Matcher worldName=Pattern.compile("(?i)^world (.+)$").matcher(s);
        if(worldName.matches()&&!worldName.group(1).toLowerCase(Locale.ROOT).startsWith("of ")){Expression name=compiler.compile(worldName.group(1));return c->{String requested=c.text(name.evaluate(c));for(ServerWorld world:server.getWorlds()){WorldRef ref=ref(world);if(ref.id().equals(requested)||worldName(ref).equals(requested))return ref;}return null;};}
        Matcher inventoryItems=Pattern.compile("(?i)^all items in (.+)$").matcher(s);
        if(inventoryItems.matches()){Expression inventory=compiler.compile(inventoryItems.group(1));return c->{Object value=inventory.evaluate(c);if(!(value instanceof InventoryRef ref))return List.of();ServerPlayerEntity player=requirePlayer(player(ref.owner()));List<Object> items=new ArrayList<>();for(int i=0;i<player.getInventory().size();i++){ItemStack stack=player.getInventory().getStack(i);if(!stack.isEmpty())items.add(new ItemValue(stack));}return items;};}
        Matcher locationCall=Pattern.compile("(?i)^location\\((.+)\\)$").matcher(s);
        if(locationCall.matches()){List<String> args=Syntax.split(locationCall.group(1),",");if(args.size()!=4)throw new Syntax.Error(compiler.source(),"Location requires x, y, z, world");List<Expression> expressions=args.stream().map(compiler::compile).toList();return c->{Object world=expressions.get(3).evaluate(c);return world instanceof WorldRef ref?new Location(ref,Values.number(expressions.get(0).evaluate(c)),Values.number(expressions.get(1).evaluate(c)),Values.number(expressions.get(2).evaluate(c)),0,0):null;};}
        Matcher inventoryAmount=Pattern.compile("(?i)^amount of (.+) in (.+)$").matcher(s);
        if(inventoryAmount.matches()){Expression requested=compiler.compile(inventoryAmount.group(1)),inventory=compiler.compile(inventoryAmount.group(2));return c->{Object a=requested.evaluate(c),b=inventory.evaluate(c);if(!(a instanceof ItemValue item)||!(b instanceof InventoryRef ref))return null;ServerPlayerEntity player=requirePlayer(player(ref.owner()));int count=0;for(int i=0;i<player.getInventory().size();i++){ItemStack stack=player.getInventory().getStack(i);if(matches(stack,item.stack))count+=stack.getCount();}return (double)count;};}
        Matcher named=Pattern.compile("(?i)^(.+) named (.+)$").matcher(s);
        if(named.matches()){Expression item=compiler.compile(named.group(1)),name=compiler.compile(named.group(2));return c->{Object v=item.evaluate(c);if(!(v instanceof ItemValue stack))return null;ItemValue out=new ItemValue(stack.stack);out.stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal(c.text(name.evaluate(c)).replace('&','§')));return out;};}
        Matcher location=Pattern.compile("(?i)^location at (.+), (.+), (.+) in (.+)$").matcher(s);
        if(location.matches()){Expression x=compiler.compile(location.group(1)),y=compiler.compile(location.group(2)),z=compiler.compile(location.group(3)),w=compiler.compile(location.group(4));return c->{Object world=w.evaluate(c);return world instanceof WorldRef ref?new Location(ref,Values.number(x.evaluate(c)),Values.number(y.evaluate(c)),Values.number(z.evaluate(c)),0,0):null;};}
        Matcher distance=Pattern.compile("(?i)^distance between (.+) and (.+)$").matcher(s);
        if(distance.matches()){Expression a=compiler.compile(distance.group(1)),b=compiler.compile(distance.group(2));return c->{Location l=api.location(a.evaluate(c)),r=api.location(b.evaluate(c));if(l==null||r==null||!l.world().equals(r.world()))return null;return NativeApiBridge.position(l).distanceTo(NativeApiBridge.position(r));};}
        return null;
    }
    @Override public Action compileEffect(String text,ExpressionCompiler compiler){
        Action nativeApi=api.effect(text,compiler);if(nativeApi!=null)return nativeApi;
        Matcher removeItem=Pattern.compile("(?i)^remove (.+) from (.+)$").matcher(text);
        if(removeItem.matches()&&Syntax.find(removeItem.group(1)," of ",false)<0){Expression item=compiler.compile(removeItem.group(1)),target=compiler.compile(removeItem.group(2));return c->{Object value=item.evaluate(c);if(!(value instanceof ItemValue requested))throw new IllegalArgumentException("Expected item to remove");Object recipient=target.evaluate(c);ServerPlayerEntity player=requirePlayer(player(recipient instanceof InventoryRef ref?ref.owner():recipient));int remaining=requested.stack.getCount();for(int i=0;i<player.getInventory().size()&&remaining>0;i++){ItemStack stack=player.getInventory().getStack(i);if(matches(stack,requested.stack)){int removed=Math.min(stack.getCount(),remaining);stack.decrement(removed);remaining-=removed;}}player.getInventory().markDirty();};}
        Matcher remove=Pattern.compile("(?i)^remove (.+) of (.+) from (.+)$").matcher(text);
        if(remove.matches()){Expression count=compiler.compile(remove.group(1)),item=compiler.compile(remove.group(2)),target=compiler.compile(remove.group(3));return c->{Object value=item.evaluate(c);if(!(value instanceof ItemValue requested))throw new IllegalArgumentException("Expected item to remove");Object recipient=target.evaluate(c);ServerPlayerEntity player=requirePlayer(player(recipient instanceof InventoryRef ref?ref.owner():recipient));int remaining=(int)Values.number(count.evaluate(c));if(remaining<0||remaining>1000000)throw new IllegalArgumentException("Invalid inventory removal amount");for(int i=0;i<player.getInventory().size()&&remaining>0;i++){ItemStack stack=player.getInventory().getStack(i);if(matches(stack,requested.stack)){int removed=Math.min(stack.getCount(),remaining);stack.decrement(removed);remaining-=removed;}}player.getInventory().markDirty();};}
        if(text.toLowerCase(Locale.ROOT).startsWith("close ")){Expression inventory=compiler.compile(text.substring(6));return c->{Object value=inventory.evaluate(c);if(!(value instanceof InventoryRef ref))throw new IllegalArgumentException("Expected player inventory");ServerPlayerEntity player=requirePlayer(player(ref.owner()));player.closeHandledScreen();vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.openMenu("svframe_close",player);};}
        return null;
    }
    private boolean matches(ItemStack actual,ItemStack requested){if(actual.getItem()!=requested.getItem())return false;Text name=requested.get(DataComponentTypes.CUSTOM_NAME);return name==null||Objects.equals(name,actual.get(DataComponentTypes.CUSTOM_NAME));}
    @Override public void error(Syntax.Source source,Throwable failure){SVFrameSkript.LOG.error("{}: {}",source,failure.getMessage());SVFrameSkript.runtimeError(source,failure);}
    @Override public void trace(Syntax.Source source,String kind,Context context){SVFrameSkript.trace(source,kind);}
}
