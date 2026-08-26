package vn.svframe.svframelib.util.configobject;

import java.util.Arrays;

public final class MissingArgumentException extends RuntimeException {
    public MissingArgumentException(String... keys){super("Missing required argument: "+String.join("/",keys==null?new String[0]:keys));}
}
