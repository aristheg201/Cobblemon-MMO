package vn.svframe.worldboss;
public final class InfinityRules {
 public static final long SNAP_COOLDOWN=1_800_000L;
 public static int select(int[] weights,int roll){if(weights==null||weights.length!=10)throw new IllegalArgumentException("Ten outcome weights required");long total=0;for(int n:weights){if(n<0)throw new IllegalArgumentException("Negative weight");total+=n;}if(total<1||total>1_000_000||roll<0||roll>=total)throw new IllegalArgumentException("Invalid outcome roll");for(int i=0;i<weights.length;i++){roll-=weights[i];if(roll<0)return i;}throw new AssertionError();}
 public static String guardianStone(String hero){return switch(hero==null?"":hero){case "FLASH","SPEEDSTER"->"space";case "THOR"->"power";case "COLLECTOR"->"reality";case "SORCERER_SUPREME"->"mind";default->"soul";};}
}
