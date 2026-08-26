package vn.svframe.svframelib.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** Native Fabric equivalent of MythicLib 1.7.1 entity anchor locations. */
public enum EntityLocationType {
    FEET(0d),
    BODY(0.5d),
    TOP(1d),
    EYES(0.888888888888889d);

    private final double heightPercentage;

    EntityLocationType(double heightPercentage) {
        this.heightPercentage = heightPercentage;
    }

    public Vec3d getLocation(Entity entity) {
        if (entity == null) throw new NullPointerException("Entity cannot be null");
        return entity.getPos().add(0d, entity.getHeight() * heightPercentage, 0d);
    }
}
