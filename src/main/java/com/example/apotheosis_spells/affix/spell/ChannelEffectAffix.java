package com.example.apotheosis_spells.affix.spell;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.SpellEffects;
import com.example.apotheosis_spells.affix.SpellAffix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;

/**
 * 吟唱增益：吟唱/蓄力/引导<b>期间每 tick 刷新</b>一个 JSON 指定的药水效果（等级随稀有度）。
 * 由 {@code SpellEffectHandler.onPlayerTick} 在施法者 {@code isCasting()} 期间实时结算；
 * 瞬发法术无吟唱阶段，则在 {@code onSpellCast} 兜底施加一次短时效果。
 */
public class ChannelEffectAffix extends SpellAffix {
    public static final Codec<ChannelEffectAffix> C = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Fn.C).fieldOf("values").forGetter(a -> a.vals),
            Codec.STRING.listOf().xmap(Set::copyOf, s -> s.stream().toList()).fieldOf("types").forGetter(a -> a.types),
            Codec.STRING.optionalFieldOf("effect", SpellEffects.DEF_CHANNEL_EFFECT).forGetter(a -> a.effect)
    ).apply(i, ChannelEffectAffix::new));

    private final String effect;

    public ChannelEffectAffix(Map<String, Fn> v, Set<String> t, String effect) {
        super("channel_effect", v, t, AffixType.POTION);
        this.effect = effect;
    }

    @Override
    public ReforgeCache.Data contribute(int baseValue) { return ReforgeCache.Data.DEF; }

    @Override
    public SpellEffects contributeEffect(int baseValue) {
        return SpellEffects.ofChannel(baseValue, effect);
    }

    @Override
    public MutableComponent getDescription(ItemStack stack, LootRarity rarity, float level) {
        int v = getBaseValue(rarity, level);
        return Component.translatable(getModifierKey(), effectComponent(effect, v - 1));
    }

    /** 把效果 id + 放大等级渲染成带颜色的「效果名 [n级]」组件。 */
    static MutableComponent effectComponent(String effectId, int amplifier) {
        MobEffect eff = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(effectId));
        MutableComponent name = eff != null
                ? Component.translatable(eff.getDescriptionId())
                : Component.literal(effectId);
        if (amplifier > 0) {
            name = Component.translatable("potion.withAmplifier", name,
                    Component.translatable("potion.potency." + amplifier));
        }
        if (eff != null) name = name.withStyle(eff.getCategory().getTooltipFormatting());
        return name;
    }

    @Override
    protected Codec<? extends SpellAffix> getSelfCodec() { return C; }
}
