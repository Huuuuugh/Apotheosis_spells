package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.api.ScrollRollScope;
import com.example.apotheosis_spells.handler.ScrollLootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 createLootItem(4 参) 的进出口维护 {@link ScrollRollScope}。
 * 3 参重载内部委托 4 参，所以只注入 4 参即可覆盖重铸台与战利品注入两条链路。
 *
 * HEAD 处无条件按类别覆盖开关（而不是只在卷轴时置 true）：createLootItem 对
 * “一个词条都凑不出来”的情况会抛 RuntimeException 而跳过 RETURN 注入，
 * 无条件覆盖保证残留标记在下一次调用时自愈。
 */
@Mixin(value = LootController.class, remap = false)
public class LootControllerMixin {

    @Inject(method = "createLootItem(Lnet/minecraft/world/item/ItemStack;Ldev/shadowsoffire/apotheosis/adventure/loot/LootCategory;Ldev/shadowsoffire/apotheosis/adventure/loot/LootRarity;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"), remap = false)
    private static void apothSpells$enterScope(ItemStack stack, LootCategory cat, LootRarity rarity, RandomSource rand, CallbackInfoReturnable<ItemStack> cir) {
        ScrollRollScope.set(cat != null && cat == ScrollLootCategory.SCROLL);
    }

    @Inject(method = "createLootItem(Lnet/minecraft/world/item/ItemStack;Ldev/shadowsoffire/apotheosis/adventure/loot/LootCategory;Ldev/shadowsoffire/apotheosis/adventure/loot/LootRarity;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN"), remap = false)
    private static void apothSpells$exitScope(ItemStack stack, LootCategory cat, LootRarity rarity, RandomSource rand, CallbackInfoReturnable<ItemStack> cir) {
        ScrollRollScope.set(false);
    }
}
