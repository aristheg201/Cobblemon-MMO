package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.ClientFeedbackQa;

/** Observes actual client sound playback requests; never creates gameplay effects. */
@Mixin(SoundSystem.class)
abstract class KunaiSoundQaMixin {
  @Inject(method="play(Lnet/minecraft/client/sound/SoundInstance;)V", at=@At("TAIL"))
  private void fantasyhub$sound(SoundInstance sound, CallbackInfo ci) {
    if (!ClientFeedbackQa.ENABLED) return;
    String id = sound.getId().toString();
    if (id.equals("fantasyhub:kabuto.gun_fire")) ClientFeedbackQa.gunSounds++;
    if (id.equals("fantasyhub:kabuto.kunai_slash")) ClientFeedbackQa.kunaiSounds++;
  }
}
