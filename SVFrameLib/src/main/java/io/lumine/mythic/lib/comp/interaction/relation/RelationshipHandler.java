package io.lumine.mythic.lib.comp.interaction.relation;
import net.minecraft.server.network.ServerPlayerEntity;
@FunctionalInterface public interface RelationshipHandler {
    Relationship getRelationship(ServerPlayerEntity first,ServerPlayerEntity second);
}
