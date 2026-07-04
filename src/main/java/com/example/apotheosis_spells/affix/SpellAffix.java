package com.example.apotheosis_spells.affix;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.SpellEffects;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;

/**
 * 法术词条基类。
 *
 * 与原 ScrollAffix 区别：
 *   - 不再用 `id.contains("spell_power")` 字符串匹配，改由每个子类的 contribute 方法给出具体 Data 字段。
 *   - canApplyTo 默认接受所有 scroll/spellbook_slot 类型。
 *   - getDescription 使用 key = "modifier.apotheosis_spells.<id>"（子类覆盖 getModifierKey 即可）。
 */
public abstract class SpellAffix extends Affix {

    public record Fn(float min, int steps, float step) {
        public static final Codec<Fn> C = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("min").forGetter(Fn::min),
                Codec.INT.fieldOf("steps").forGetter(Fn::steps),
                Codec.FLOAT.fieldOf("step").forGetter(Fn::step)
        ).apply(i, Fn::new));

        public int get(float lvl) {
            return Mth.floor(min + steps * step * lvl);
        }
    }

    protected final String mod;
    protected final Map<String, Fn> vals;
    protected final Set<String> types;

    protected SpellAffix(String mod, Map<String, Fn> vals, Set<String> types, AffixType type) {
        super(type);
        this.mod = mod;
        this.vals = vals;
        this.types = types;
    }

    public int getBaseValue(LootRarity r, float lvl) {
        Fn f = vals.get(rarityKey(r));
        return f != null ? f.get(lvl) : Mth.floor(lvl);
    }

    protected static String rarityKey(LootRarity r) {
        var key = dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry.INSTANCE.getKey(r);
        return key != null ? key.getPath() : "";
    }

    /**
     * 把 getBaseValue() 的数值转换为 ReforgeCache.Data 的偏移。
     * 子类必须实现。
     */
    public abstract ReforgeCache.Data contribute(int baseValue);

    /**
     * 「事件类特效」贡献（吸血/暴击/斩杀/超载/回响/…）。默认无特效；事件类词条子类覆写。
     * 与 {@link #contribute}（倍率类）并行，由 {@link ReforgeCache#computeEffects} 单独聚合，
     * 在 {@code handler.SpellEffectHandler} 的事件里结算。
     */
    public SpellEffects contributeEffect(int baseValue) {
        return SpellEffects.NONE;
    }

    @Override
    public boolean canApplyTo(ItemStack stack, LootCategory cat, LootRarity rarity) {
        // 稀有度必须在 values 里显式定义，否则 JEI 会展示不存在的稀有度组合，
        // 重铸也会 roll 出无数值定义的档位（getBaseValue 兜底值掩盖问题）。
        return types.contains(cat.getName()) && vals.containsKey(rarityKey(rarity));
    }

    @Override
    public MutableComponent getDescription(ItemStack stack, LootRarity rarity, float level) {
        int v = getBaseValue(rarity, level);
        return Component.translatable(getModifierKey(), v);
    }

    public String getModifierKey() {
        return "modifier.apotheosis_spells." + mod;
    }

    @Override
    public Component getName(boolean prefix) {
        return Component.translatable("affix.apotheosis_spells." + mod + (prefix ? "" : ".suffix"));
    }

    @Override
    public Codec<? extends Affix> getCodec() {
        return getSelfCodec();
    }

    /**
     * 子类需要实现自己的 codec。
     */
    protected abstract Codec<? extends SpellAffix> getSelfCodec();
}
