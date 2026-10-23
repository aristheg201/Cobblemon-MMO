package vn.svframe.fantasyhub.core;
/** MMO invokes this before parsing class icons, independent of Fabric main entrypoint order. */
public final class RegistryBootstrap implements vn.svframe.svframemmo.api.integration.SkillSourceBootstrap {
 @Override public void registerSkillSources() { vn.svframe.fantasyhub.protocol.Protocol.install(); }
}
