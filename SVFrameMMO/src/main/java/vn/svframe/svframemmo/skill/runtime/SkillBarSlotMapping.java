package vn.svframe.svframemmo.skill.runtime;

public final class SkillBarSlotMapping {
   private SkillBarSlotMapping() {
   }

   public static int displayedSlot(int castSlot, int heldSlot) {
      return castSlot + (heldSlot < castSlot ? 1 : 0);
   }
}
