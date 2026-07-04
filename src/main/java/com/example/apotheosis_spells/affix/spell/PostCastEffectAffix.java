package com.example.apotheosis_spells.affix.spell;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.SpellEffects;
import com.example.apotheosis_spells.affix.SpellAffix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;

/**
 * 施法增益：一次施法<b>结束后</b>施加一个 JSON 指定的药水效果（等级随稀有度，持续 {@code duration} tick）。
 * 由 {@code SpellEffectHandler.onPlayerTick} 在施法者 {@code isCasting()} 由 true→false 的那一刻结算
 * （蓄力/持续法术＝松手/停引导后）；瞬发法术则在 {@code onSpellCast} 兜底施加。
 */
public class PostCastEffectAffix extends SpellAffix {
    /** 默认持续时间（tick），JSON 未指定时使用。 */
    public static final int DEFAULT_DURATION = 100;

    public static final Codec<PostCastEffectAffix> C = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Fn.C).fieldOf("values").forGetter(a -> a.vals),
            Codec.STRING.listOf().xmap(Set::copyOf, s -> s.stream().toList()).fieldOf("types").forGetter(a -> a.types),
            Codec.STRING.optionalFieldOf("effect", SpellEffects.DEF_POSTCAST_EFFECT).forGetter(a -> a.effect),
            Codec.INT.optionalFieldOf("duration", DEFAULT_DURATION).forGetter(a -> a.duration)
    ).apply(i, PostCastEffectAffix::new));

    private final String effect;
    private final int duration;

    public PostCastEffectAffix(Map<String, Fn> v, Set<String> t, String effect, int duration) {
        super("postcast_effect", v, t, AffixType.POTION);
        this.effect = effect;
        this.duration = duration;
    }

    @Override
    public ReforgeCache.Data contribute(int baseValue) { return ReforgeCache.Data.DEF; }

    @Override
    public SpellEffects contributeEffect(int baseValue) {
        return SpellEffects.ofPostcast(baseValue, duration, effect);
    }

    @Override
    public MutableComponent getDescription(ItemStack stack, LootRarity rarity, float level) {
        int v = getBaseValue(rarity, level);
        return Component.translatable(getModifierKey(), ChannelEffectAffix.effectComponent(effect, v - 1));
    }

    @Override
    protected Codec<? extends SpellAffix> getSelfCodec() { return C; }
}
