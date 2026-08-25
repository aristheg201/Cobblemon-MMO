package io.lumine.mythic.lib.api.condition.type;
import io.lumine.mythic.lib.api.MMOLineConfig;
public abstract class MMOCondition {
    protected final MMOLineConfig config;
    public MMOCondition(MMOLineConfig config){this.config=config;}
    public MMOLineConfig getConfig(){return config;}
}
