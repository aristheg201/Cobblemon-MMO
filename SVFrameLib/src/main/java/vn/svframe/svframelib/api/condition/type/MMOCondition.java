package vn.svframe.svframelib.api.condition.type;
import vn.svframe.svframelib.api.MMOLineConfig;
public abstract class MMOCondition {
    protected final MMOLineConfig config;
    public MMOCondition(MMOLineConfig config){this.config=config;}
    public MMOLineConfig getConfig(){return config;}
}
