package vn.svframe.fantasyhub.client;

/** Report the actual failing packaged file instead of treating every failure as a missing mod. */
final class AssetIntegrity {
    private AssetIntegrity(){}
    static String diagnose(){
        var container=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("fantasyhubclient").orElse(null);
        if(container==null)return "fantasyhubclient mod container is missing";
        return AssetVerifier.diagnose(name->container.findPath(name).orElse(null),vn.svframe.fantasyhub.protocol.Protocol.ASSET_REVISION);
    }
}
