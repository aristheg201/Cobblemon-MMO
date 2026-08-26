package vn.svframe.svframelib.api;

import vn.svframe.svframelib.util.configobject.JsonWrapper;
import java.util.Arrays;

public class MMOLineConfig extends JsonWrapper {
    private final String key;
    private final String[] args;
    public MMOLineConfig(String line){super(line);String input=line==null?"":line.trim();int open=input.indexOf('{'),close=input.lastIndexOf('}');if(open>=0&&close>=open){this.key=input.substring(0,open).trim();String tail=input.substring(Math.min(input.length(),close+1)).trim();this.args=tail.isEmpty()?new String[0]:tail.split("\\s+");}else{String[] split=input.isEmpty()?new String[0]:input.split("\\s+");this.key=split.length==0?"":split[0];this.args=split.length<=1?new String[0]:Arrays.copyOfRange(split,1,split.length);}}
    public String[] args(){return args.clone();}
    @Override public String getKey(){return key;}
    public void validate(String...keys){validateKeys(keys);}
    @Override public String toString(){return key+asMap();}
}
