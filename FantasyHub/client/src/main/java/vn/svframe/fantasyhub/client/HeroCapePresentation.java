package vn.svframe.fantasyhub.client;

/** Batman Back cape overrides skin art; Thor and Strange retain personal-cape priority. */
final class HeroCapePresentation {
  static String select(boolean personal,boolean batman,boolean thor,boolean strange,String armor){
    return batman?"batman":fallback(personal,thor,strange,armor);
  }
  static String fallback(boolean personalCapeVisible,boolean thorBack,boolean strangeBack,String armor){
    if(personalCapeVisible)return "";
    if(thorBack)return "thor";
    if(strangeBack)return "sorcerer_supreme";
    return java.util.Set.of("thor","sorcerer_supreme","batman").contains(armor)?armor:"";
  }
}
