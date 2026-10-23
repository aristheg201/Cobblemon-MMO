package vn.svframe.svframemmo.api.player;
/** Bounded server-owned recovery mail needs serialized item data, unlike UI preferences. */
public final class PreferenceLimits {
 private PreferenceLimits(){}
 public static int maximum(String key){return "fantasyhub:mail".equals(key)?1_048_576:160;}
}
