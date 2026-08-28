package vn.svframe.svframemmo.api.integration;

/** Native Fabric entrypoint contract for integration mods that contribute skill-handler sources before class definitions load. */
@FunctionalInterface
public interface SkillSourceBootstrap {
    void registerSkillSources();
}
