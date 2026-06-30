package com.example.apotheosis_spells.handler;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingRecipe;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 为「神化回收台回收法术卷轴」构造一个<b>动态合成配方</b>,产出:
 * <ul>
 *   <li>墨水:按卷轴<b>法术自身</b>的铁魔法稀有度 → {@code irons_spellbooks:<rarity>_ink}（rarity 在前,如 common_ink）；</li>
 *   <li>纸：{@code minecraft:paper}；</li>
 *   <li>稀有度材料：按卷轴的<b>神化</b>稀有度 → {@code apotheosis:<rarity>_material}（玄奥沙=epic、神铸珍珠=mythic）。</li>
 * </ul>
 * 由 {@link com.example.apotheosis_spells.mixin.SalvagingMenuMixin} 注入 {@code SalvagingMenu.findMatch}：
 * 卷轴输入时返回本配方。因为回收台的<b>灰色虚像预览</b>(SalvagingScreen.computeResults)和<b>实际产出</b>
 * (SalvagingMenu.salvageItem) 都通过 {@code findMatch(stack).getOutputs()} 取产出,这一处即可同时修正两者,
 * 且无需自定义 Ingredient、不改动原版配方。
 */
public final class ScrollSalvage {

    private ScrollSalvage() {}

    private static final ResourceLocation DYN_ID = new ResourceLocation("apotheosis_spells", "scroll_salvage_dynamic");
    private static Constructor<SalvagingRecipe.OutputData> OUTPUT_CTOR;

    /** 卷轴 → 合成回收配方；非卷轴返回 null（交回 Apotheosis 原逻辑）。 */
    public static SalvagingRecipe recipeFor(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof Scroll)) return null;
        List<SalvagingRecipe.OutputData> outs = new ArrayList<>(3);

        // —— 墨水：按法术稀有度，<rarity>_ink ——
        try {
            SpellData sd = ISpellContainer.get(stack).getSpellAtIndex(0);
            SpellRarity sr = sd.getSpell().getRarity(sd.getLevel());
            // 墨水 0–1：一瓶墨水即可制作一张卷轴，回收给 50% 概率返还 1 个墨水（平衡，避免无损循环）。
            if (sr != null) addOut(outs, item("irons_spellbooks", sr.name().toLowerCase(Locale.ROOT) + "_ink"), 0, 1);
        } catch (Exception ignored) {
            // 卷轴异常/无法术时跳过墨水，保留纸+材料
        }

        // —— 纸 0–1（同墨水，50% 返还 1 张）——
        addOut(outs, Items.PAPER, 0, 1);

        // —— 稀有度材料：按神化稀有度 ——
        DynamicHolder<LootRarity> rarity = AffixHelper.getRarity(stack);
        if (rarity != null && rarity.isBound()) {
            ResourceLocation rid = RarityRegistry.INSTANCE.getKey(rarity.get());
            if (rid != null) addOut(outs, item("apotheosis", rid.getPath() + "_material"), 1, 4);
        }

        if (outs.isEmpty()) return null;
        return new SalvagingRecipe(DYN_ID, outs, Ingredient.EMPTY);
    }

    private static void addOut(List<SalvagingRecipe.OutputData> outs, Item item, int min, int max) {
        if (item == null || item == Items.AIR) return;
        SalvagingRecipe.OutputData d = makeOutput(new ItemStack(item), min, max);
        if (d != null) outs.add(d);
    }

    private static Item item(String namespace, String path) {
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, path));
        return (i == null || i == Items.AIR) ? null : i;
    }

    /** OutputData 的构造器是包级私有，跨包用反射构造（运行期有效，编译期不直接引用构造器）。 */
    @SuppressWarnings("unchecked")
    private static SalvagingRecipe.OutputData makeOutput(ItemStack stack, int min, int max) {
        try {
            if (OUTPUT_CTOR == null) {
                OUTPUT_CTOR = (Constructor<SalvagingRecipe.OutputData>) SalvagingRecipe.OutputData.class
                    .getDeclaredConstructor(ItemStack.class, int.class, int.class);
                OUTPUT_CTOR.setAccessible(true);
            }
            return OUTPUT_CTOR.newInstance(stack, min, max);
        } catch (Throwable t) {
            return null;
        }
    }
}
