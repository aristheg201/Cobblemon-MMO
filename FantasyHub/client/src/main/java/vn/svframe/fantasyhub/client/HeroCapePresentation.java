package vn.svframe.fantasyhub.client;

/** A visible personal skin cape always wins; equipped/default hero capes are fallback art. */
final class HeroCapePresentation {
  static String fallback(boolean personalCapeVisible,boolean thorBack,boolean strangeBack,String armor){
    if(personalCapeVisible)return "";
    if(thorBack)return "thor";
    if(strangeBack)return "sorcerer_supreme";
    return java.util.Set.of("thor","sorcerer_supreme","batman").contains(armor)?armor:"";
  }
}
