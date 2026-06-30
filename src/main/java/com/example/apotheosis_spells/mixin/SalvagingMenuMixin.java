package com.example.apotheosis_spells.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.example.apotheosis_spells.handler.ScrollSalvage;

import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 接管神化回收台对「法术卷轴」的产出。注入 {@code SalvagingMenu.findMatch}：卷轴输入时返回一个
 * 动态合成配方（墨水按法术稀有度 + 纸 + 神化稀有度材料），见 {@link ScrollSalvage}。
 *
 * <p>为什么 hook findMatch 而非 salvageItem：回收台的<b>灰色虚像预览</b>
 * (SalvagingScreen.computeResults) 与<b>实际产出</b>(SalvagingMenu.salvageItem) 都通过
 * {@code findMatch(stack).getOutputs()} 取产出。在 findMatch 处统一返回合成配方，预览与实际同时正确，
 * 且 findMatch != null 也让回收槽接受卷轴。非卷轴返回 null → 照常走原版配方匹配。
 */
@Mixin(value = SalvagingMenu.class, remap = false)
public class SalvagingMenuMixin {

    @Inject(method = "findMatch", at = @At("HEAD"), cancellable = true, remap = false)
    private static void apothSpells_findMatch(Level level, ItemStack stack, CallbackInfoReturnable<SalvagingRecipe> cir) {
        SalvagingRecipe recipe = ScrollSalvage.recipeFor(stack);
        if (recipe != null) cir.setReturnValue(recipe);
    }
}
