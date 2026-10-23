package com.novaclient.mixin;

import com.novaclient.module.NameOverride;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the Custom / Random name modes of the Name Scrambler card to players. */
@Mixin(value = Entity.class, priority = 1500)
public abstract class NovaEntityNameMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, require = 0)
    private void nova$name(CallbackInfoReturnable<Text> cir) {
        if (!NameOverride.enabled || !((Object) this instanceof PlayerEntity)) return;
        Text orig = cir.getReturnValue();
        if (orig == null) return;
        String rep = NameOverride.replace(orig.getString(), (Object) this == MinecraftClient.getInstance().player);
        if (rep != null) cir.setReturnValue(Text.literal(rep).setStyle(orig.getStyle()));
    }
}
