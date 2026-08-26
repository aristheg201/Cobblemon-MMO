package vn.svframe.svframelib.script.mechanic.shaped;

import vn.svframe.svframelib.script.mechanic.RawMechanic;
import vn.svframe.svframelib.util.configobject.ConfigObject;

public class RayTraceMechanic extends RawMechanic {
    public enum RayTraceType { DEFAULT, BLOCKS, ENTITIES }
    public RayTraceMechanic(ConfigObject config) { super("raytrace" + (config == null ? "" : "{" + config.getKeys().size() + "}")); }
}
