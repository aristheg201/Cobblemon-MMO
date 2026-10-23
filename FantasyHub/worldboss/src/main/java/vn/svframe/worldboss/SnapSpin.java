package vn.svframe.worldboss;
/** Port of ShadowCrates 2.3 CSGO shift/deceleration algorithm. Tick scheduling replaces its executor. */
final class SnapSpin {
 static void advance(int[] row,int step,int recorded,int filler){System.arraycopy(row,1,row,0,row.length-1);row[row.length-1]=step<55?filler:recorded;}
 static long delay(int step){return Math.max(1,(long)Math.ceil(Math.min(100,50*Math.pow(1.1,step/60d*10))/50d));}
}
