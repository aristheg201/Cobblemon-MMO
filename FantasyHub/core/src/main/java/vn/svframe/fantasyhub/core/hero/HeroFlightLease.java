package vn.svframe.fantasyhub.core.hero;

/** Captures only our temporary grant; vanilla player persistence stores the baseline. */
final class HeroFlightLease {
  record Abilities(boolean allowed,boolean flying,float speed){}
  private Abilities baseline;
  void acquire(Abilities current){if(baseline==null)baseline=current;}
  boolean owned(){return baseline!=null;}
  Abilities saved(Abilities current){return baseline==null?current:baseline;}
  Abilities release(Abilities current,boolean creativeOrSpectator){var restored=baseline==null||creativeOrSpectator?current:baseline;baseline=null;return restored;}
}
