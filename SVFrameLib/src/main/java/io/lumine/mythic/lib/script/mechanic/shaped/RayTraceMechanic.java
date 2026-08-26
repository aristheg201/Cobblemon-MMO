package io.lumine.mythic.lib.script.mechanic.shaped;

import io.lumine.mythic.lib.script.mechanic.RawMechanic;
import io.lumine.mythic.lib.util.configobject.ConfigObject;

public class RayTraceMechanic extends RawMechanic {
    public enum RayTraceType { DEFAULT, BLOCKS, ENTITIES }
    public RayTraceMechanic(ConfigObject config) { super("raytrace" + (config == null ? "" : "{" + config.getKeys().size() + "}")); }
}
