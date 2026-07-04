package com.example.apotheosis_spells.affix.spell;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.affix.SpellAffix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;

import java.util.Map;
import java.util.Set;

/** 吟唱时间 -N%（吟唱乘算 = 1 - N/100） */
public class CastTimeAffix extends SpellAffix {
    public static final Codec<CastTimeAffix> C = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("modifier").forGetter(a -> a.mod),
            Codec.unboundedMap(Codec.STRING, Fn.C).fieldOf("values").forGetter(a -> a.vals),
            Codec.STRING.listOf().xmap(Set::copyOf, s -> s.stream().toList()).fieldOf("types").forGetter(a -> a.types)
    ).apply(i, CastTimeAffix::new));

    public CastTimeAffix(String m, Map<String, Fn> v, Set<String> t) { super(m, v, t, AffixType.POTION); }

    @Override
    public ReforgeCache.Data contribute(int baseValue) {
        return new ReforgeCache.Data(1, 1, 1, Math.max(0.05f, 1f - baseValue / 100f), 0, 1, 1, 0);
    }

    @Override
    protected Codec<? extends SpellAffix> getSelfCodec() { return C; }
}
