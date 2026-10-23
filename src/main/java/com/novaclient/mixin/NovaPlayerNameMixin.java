package com.novaclient.mixin;

import com.novaclient.module.NameOverride;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Same as NovaEntityNameMixin, for versions where PlayerEntity declares its own getName(). */
@Mixin(value = PlayerEntity.class, priority = 1500)
public abstract class NovaPlayerNameMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, require = 0)
    private void nova$name(CallbackInfoReturnable<Text> cir) {
        if (!NameOverride.enabled) return;
        Text orig = cir.getReturnValue();
        if (orig == null) return;
        String rep = NameOverride.replace(orig.getString(), (Object) this == MinecraftClient.getInstance().player);
        if (rep != null) cir.setReturnValue(Text.literal(rep).setStyle(orig.getStyle()));
    }
}
