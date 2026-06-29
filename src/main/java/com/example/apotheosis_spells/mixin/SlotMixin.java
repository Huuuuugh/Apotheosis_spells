package com.example.apotheosis_spells.mixin;

import dev.shadowsoffire.placebo.menu.FilteredSlot;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FilteredSlot.class, remap = false)
public class SlotMixin {
    @Inject(method = "m_5857_", at = @At("HEAD"), cancellable = true, remap = false)
    private void accept(ItemStack s, CallbackInfoReturnable<Boolean> cir) {
        if (s.getItem() instanceof Scroll) cir.setReturnValue(true);
    }
}
