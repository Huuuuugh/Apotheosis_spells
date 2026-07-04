package com.example.apotheosis_spells.affix.spell;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.affix.SpellAffix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;

import java.util.Map;
import java.util.Set;

/** 法术范围 +N%（范围乘算 = 1 + N/100） */
public class SpellRadiusAffix extends SpellAffix {
    public static final Codec<SpellRadiusAffix> C = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("modifier").forGetter(a -> a.mod),
            Codec.unboundedMap(Codec.STRING, Fn.C).fieldOf("values").forGetter(a -> a.vals),
            Codec.STRING.listOf().xmap(Set::copyOf, s -> s.stream().toList()).fieldOf("types").forGetter(a -> a.types)
    ).apply(i, SpellRadiusAffix::new));

    public SpellRadiusAffix(String m, Map<String, Fn> v, Set<String> t) { super(m, v, t, AffixType.POTION); }

    @Override
    public ReforgeCache.Data contribute(int baseValue) {
        return new ReforgeCache.Data(1, 1, 1, 1, 0, 1f + baseValue / 100f, 1, 0);
    }

    @Override
    protected Codec<? extends SpellAffix> getSelfCodec() { return C; }
}
