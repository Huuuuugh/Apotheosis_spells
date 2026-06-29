package com.example.apotheosis_spells.gem;

import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * SpellGemBonus 注册表。
 *
 * 因为 Apotheosis 的 GemBonusCodec 不允许直接加载外部注册的 codec，
 * 我们用 gem tag "gem" 字段 + id 反查映射到 SpellGemBonus 子类。
 */
public class GemRegistryHook {

    private static final Map<String, Supplier<? extends SpellGemBonus>> FACTORIES = new HashMap<>();

    public static void register(String gemId, Supplier<? extends SpellGemBonus> factory) {
        FACTORIES.put(gemId, factory);
    }

    public static SpellGemBonus getForGemStack(ItemStack gemStack, LootRarity rarity) {
        if (gemStack.isEmpty()) return null;
        var tag = gemStack.getTag();
        if (tag == null) return null;
        String gemId = tag.getString("gem");
        if (gemId.isEmpty()) return null;
        Supplier<? extends SpellGemBonus> f = FACTORIES.get(gemId);
        if (f == null) return null;
        SpellGemBonus b = f.get();
        return b != null && b.supports(rarity) ? b : null;
    }

    /**
     * 默认注册两个内置 Gem
     */
    public static void registerDefaults() {
        register("apotheosis_spells:mage_slayer", MageSlayerGemBonus::new);
        register("apotheosis_spells:spell_specialist", SpellSpecialistGemBonus::new);
    }

    public static Set<String> registeredIds() {
        return FACTORIES.keySet();
    }
}
