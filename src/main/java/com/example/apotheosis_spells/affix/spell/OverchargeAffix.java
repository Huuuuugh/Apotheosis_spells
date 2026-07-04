package com.example.apotheosis_spells.affix.spell;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.SpellEffects;
import com.example.apotheosis_spells.affix.SpellAffix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;

import java.util.Map;
import java.util.Set;

/** 超载：N% 几率本次施法等级 +1（事件类，CastMixin.apoth_boostCastLevel 在施法时 roll）。 */
public class OverchargeAffix extends SpellAffix {
    public static final Codec<OverchargeAffix> C = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("modifier").forGetter(a -> a.mod),
            Codec.unboundedMap(Codec.STRING, Fn.C).fieldOf("values").forGetter(a -> a.vals),
            Codec.STRING.listOf().xmap(Set::copyOf, s -> s.stream().toList()).fieldOf("types").forGetter(a -> a.types)
    ).apply(i, OverchargeAffix::new));

    public OverchargeAffix(String m, Map<String, Fn> v, Set<String> t) { super(m, v, t, AffixType.POTION); }

    @Override
    public ReforgeCache.Data contribute(int baseValue) { return ReforgeCache.Data.DEF; }

    @Override
    public SpellEffects contributeEffect(int baseValue) { return SpellEffects.ofOvercharge(baseValue / 100f); }

    @Override
    protected Codec<? extends SpellAffix> getSelfCodec() { return C; }
}
