package com.example.apotheosis_spells.handler;

import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Apotheosis LootCategory 适配。
 *
 * - SCROLL：作用在 Scroll 本体（即 SpellSlot 0 的 SpellSlot 子标签）。
 *           Scroll 整体可放入重铸台，词条作用于 SpellSlot 内的法术。
 * - SPELLBOOK_SLOT：作用在 SpellBook 内的每一个 SpellSlot。
 *                   SpellBook 本体不可单独重铸，只有 SpellSlot 内的法术被重铸过 Scroll 后铭刻进来时才有词条。
 */
public class ScrollLootCategory {

    public static LootCategory SCROLL;
    public static LootCategory SPELLBOOK_SLOT;

    /**
     * 注册 scroll LootCategory。
     * Scroll 整体可作为 affix 物品被重铸台处理。
     * SpellBook 内的 SpellSlot 通过 copyAffixData 从 Scroll 复制 affix_data，无需独立类别。
     */
    public static void register() {
        if (SCROLL == null) {
            SCROLL = LootCategory.register(null, "scroll",
                    (ItemStack stack) -> stack.getItem() instanceof Scroll,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
        }
    }
}
