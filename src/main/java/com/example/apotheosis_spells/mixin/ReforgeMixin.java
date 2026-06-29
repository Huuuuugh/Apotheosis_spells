package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.api.ReforgeCache;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LootController.class, remap = false)
public class ReforgeMixin {
    @Inject(method = "createLootItem", at = @At("RETURN"))
    private static void after(ItemStack stack, dev.shadowsoffire.apotheosis.adventure.loot.LootRarity rarity,
                               net.minecraft.util.RandomSource rand, CallbackInfoReturnable<ItemStack> cir) {
        if (cir.getReturnValue().getItem() instanceof Scroll) ReforgeCache.sync(cir.getReturnValue());
    }
}
