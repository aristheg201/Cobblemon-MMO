package io.lumine.mythic.lib.player.particle;

import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.player.particle.type.*;
import io.lumine.mythic.lib.util.TriFunction;
import io.lumine.mythic.lib.util.configobject.ConfigObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Registry of the seven MythicLib 1.7.1 player particle effects. */
public class ParticleEffectType {
    private static final Map<String,ParticleEffectType> BY_NAME = new LinkedHashMap<>();

    private final Function<ConfigObject,ParticleEffect> parser;
    private final TriFunction<String,Map<String,Double>,ParticleInformation,ParticleEffect> instantiator;
    private final String id, name, description;
    private final int period;
    private final boolean priority;
    private final Map<String,Double> modifiers;

    public ParticleEffectType(String id, Function<ConfigObject,ParticleEffect> parser,
                              TriFunction<String,Map<String,Double>,ParticleInformation,ParticleEffect> instantiator,
                              int period, boolean priority, String description, Map<String,Double> modifiers) {
        this.id = UtilityMethods.enumName(id);
        this.parser = Objects.requireNonNull(parser);
        this.instantiator = Objects.requireNonNull(instantiator);
        this.name = UtilityMethods.caseOnWords(this.id.toLowerCase().replace('_',' '));
        this.period = period;
        this.priority = priority;
        this.description = Objects.requireNonNull(description);
        this.modifiers = Map.copyOf(modifiers);
    }

    public String getId(){return id;} public String getName(){return name;} public String getDescription(){return description;}
    public int getPeriod(){return period;} public boolean hasPriority(){return priority;}
    public Function<ConfigObject,ParticleEffect> getParser(){return parser;}
    public TriFunction<String,Map<String,Double>,ParticleInformation,ParticleEffect> getInstantiator(){return instantiator;}
    public Set<String> getModifiers(){return modifiers.keySet();}
    public double getDefaultModifierValue(String modifier){return Objects.requireNonNull(modifiers.get(modifier),"Modifier '"+modifier+"' not found");}

    public static final ParticleEffectType AURA = new ParticleEffectType("AURA", AuraParticleEffect::new, AuraParticleEffect::new,1,true,"Particles flying around you.",Map.of("amount",3d,"speed",0d,"rotation-speed",1d,"y-speed",1d,"y-offset",.7d,"radius",1.3d,"height",1d));
    public static final ParticleEffectType DOUBLE_RINGS = new ParticleEffectType("DOUBLE_RINGS", DoubleRingsParticleEffect::new, DoubleRingsParticleEffect::new,1,true,"Particles drawing two rings around you.",Map.of("radius",.8d,"y-offset",.4d,"height",1d,"speed",0d,"rotation-speed",1d));
    public static final ParticleEffectType FIREFLIES = new ParticleEffectType("FIREFLIES", FirefliesParticleEffect::new, FirefliesParticleEffect::new,1,true,"A horizontal swirl of particles around you.",Map.of("amount",3d,"speed",0d,"rotation-speed",1d,"radius",1.3d,"height",1d));
    public static final ParticleEffectType GALAXY = new ParticleEffectType("GALAXY", GalaxyParticleEffect::new, GalaxyParticleEffect::new,1,true,"Particles flying outwards; looks like a galaxy.",Map.of("height",1d,"speed",1d,"y-coord",0d,"rotation-speed",1d,"amount",6d));
    public static final ParticleEffectType HELIX = new ParticleEffectType("HELIX", HelixParticleEffect::new, HelixParticleEffect::new,1,true,"Particles flying around you, forming a sphere.",Map.of("radius",.8d,"height",.6d,"rotation-speed",1d,"y-speed",1d,"amount",4d,"speed",0d));
    public static final ParticleEffectType OFFSET = new ParticleEffectType("OFFSET", OffsetParticleEffect::new, OffsetParticleEffect::new,5,false,"Some particles randomly spawning around your body.",Map.of("amount",5d,"vertical-offset",.5d,"horizontal-offset",.3d,"speed",0d,"height",1d));
    public static final ParticleEffectType VORTEX = new ParticleEffectType("VORTEX", VortexParticleEffect::new, VortexParticleEffect::new,1,true,"Particles flying around you in a cone shape.",Map.of("radius",1.5d,"height",2.4d,"speed",0d,"y-speed",1d,"rotation-speed",1d,"amount",3d));

    static { register(AURA);register(DOUBLE_RINGS);register(FIREFLIES);register(GALAXY);register(HELIX);register(OFFSET);register(VORTEX); }
    public static boolean isValid(String id){return BY_NAME.containsKey(UtilityMethods.enumName(id));}
    public static Collection<ParticleEffectType> getAll(){return List.copyOf(BY_NAME.values());}
    public static ParticleEffectType get(String id){return Objects.requireNonNull(BY_NAME.get(UtilityMethods.enumName(id)),"Could not find particle effect type with ID "+id);}
    public static void register(ParticleEffectType type){Objects.requireNonNull(type);if(BY_NAME.putIfAbsent(type.id,type)!=null)throw new IllegalArgumentException("A particle effect type with the same ID already exists");}
}
