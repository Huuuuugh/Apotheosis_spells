package com.example.apotheosis_spells.gem;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.handler.ScrollLootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemClass;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * 法术专精 Gem：
 *   - common: dmg+5%, mana-3%
 *   - uncommon: dmg+8%, mana-5%
 *   - rare: dmg+12%, mana-8%, cd-5%
 *   - epic: dmg+18%, mana-10%, cd-8%
 *   - mythic: dmg+25%, mana-15%, cd-12%
 */
public class MageSlayerGemBonus extends SpellGemBonus {

    public static final GemClass SCROLL_CLASS = new GemClass("scroll_spell",
            Set.of(ScrollLootCategory.SCROLL, ScrollLootCategory.SPELLBOOK_SLOT));

    public MageSlayerGemBonus() {
        super(new ResourceLocation("apotheosis_spells", "mage_slayer"), SCROLL_CLASS);
    }

    @Override
    public boolean supports(LootRarity rarity) {
        return true;
    }

    @Override
    public com.mojang.serialization.Codec<? extends dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.GemBonus> getCodec() {
        throw new UnsupportedOperationException("SpellGemBonus is not registered via Apotheosis codec.");
    }

    @Override
    public ReforgeCache.Data contribute(LootRarity rarity) {
        float dmg = 1, mana = 1, cd = 1;
        String key = dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry.INSTANCE.getKey(rarity).getPath();
        switch (key) {
            case "common" -> { dmg = 1.05f; mana = 0.97f; }
            case "uncommon" -> { dmg = 1.08f; mana = 0.95f; }
            case "rare" -> { dmg = 1.12f; mana = 0.92f; cd = 0.95f; }
            case "epic" -> { dmg = 1.18f; mana = 0.90f; cd = 0.92f; }
            case "mythic" -> { dmg = 1.25f; mana = 0.85f; cd = 0.88f; }
            default -> { dmg = 1.10f; mana = 0.95f; }
        }
        return new ReforgeCache.Data(dmg, mana, cd, 1, 0, 1, 1, 0);
    }
}
