package com.example.apotheosis_spells.gem;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.mojang.serialization.Codec;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemClass;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.GemBonus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 作用于法术的 Gem 基础奖励。
 *
 * 设计：SpellGemBonus 不需要单独 CodecMap 注册（Apotheosis 端不支持 hot-load）。
 * Gem 的"法术加成"直接通过硬编码的子类（FireGemBonus / IceGemBonus 等）实现，
 * ReforgeCache.computeData 通过 instanceof SpellGemBonus 分支调用 contribute(rarity)。
 */
public abstract class SpellGemBonus extends GemBonus {

    public SpellGemBonus(ResourceLocation id, GemClass gemClass) {
        super(id, gemClass);
    }

    public abstract ReforgeCache.Data contribute(LootRarity rarity);

    @Override
    public abstract Codec<? extends GemBonus> getCodec();

    @Override
    public GemBonus validate() {
        return this;
    }

    @Override
    public boolean supports(LootRarity rarity) {
        return true;
    }

    @Override
    public int getNumberOfUUIDs() {
        return 0;
    }

    @Override
    public Component getSocketBonusTooltip(net.minecraft.world.item.ItemStack gem, LootRarity rarity) {
        return Component.translatable("gem_bonus.apotheosis_spells." + this.getId().getPath());
    }
}
