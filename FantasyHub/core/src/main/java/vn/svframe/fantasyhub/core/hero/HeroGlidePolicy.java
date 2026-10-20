package vn.svframe.fantasyhub.core.hero;
/** Never replace jump ascent with glide descent. Native Elytra physics takes over after the apex. */
final class HeroGlidePolicy {
 static boolean shouldGlide(boolean eligible,boolean jump,boolean grounded,boolean blocked,boolean active,double verticalSpeed){
  return eligible&&jump&&!grounded&&!blocked&&(active||verticalSpeed<-.05);
 }
}
