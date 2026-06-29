package com.example.apotheosis_spells.gem;

import com.example.apotheosis_spells.api.ReforgeCache;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import net.minecraft.resources.ResourceLocation;

/**
 * 法术通用强化 Gem：dmg+r, cd-r, cast_time-r 等。
 * 用于通用强化（不分学派）。
 */
public class SpellSpecialistGemBonus extends SpellGemBonus {
    public SpellSpecialistGemBonus() {
        super(new ResourceLocation("apotheosis_spells", "spell_specialist"), MageSlayerGemBonus.SCROLL_CLASS);
    }

    @Override
    public ReforgeCache.Data contribute(LootRarity rarity) {
        float dmg = 1, cd = 1, cast = 1, lvl = 0;
        String key = dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry.INSTANCE.getKey(rarity).getPath();
        switch (key) {
            case "common" -> { dmg = 1.03f; cast = 0.95f; }
            case "uncommon" -> { dmg = 1.06f; cast = 0.92f; }
            case "rare" -> { dmg = 1.10f; cd = 0.95f; cast = 0.90f; }
            case "epic" -> { dmg = 1.15f; cd = 0.92f; cast = 0.85f; }
            case "mythic" -> { dmg = 1.20f; cd = 0.88f; cast = 0.80f; lvl = 1; }
            default -> { dmg = 1.10f; }
        }
        return new ReforgeCache.Data(dmg, 1, cd, cast, (int) lvl, 1, 1, 0);
    }

    @Override
    public com.mojang.serialization.Codec<? extends dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.GemBonus> getCodec() {
        throw new UnsupportedOperationException("SpellGemBonus is not registered via Apotheosis codec.");
    }
}
