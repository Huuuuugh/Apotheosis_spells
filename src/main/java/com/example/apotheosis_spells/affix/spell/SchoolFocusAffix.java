package com.example.apotheosis_spells.affix.spell;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.affix.SpellAffix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;

import java.util.Map;
import java.util.Set;

/** 学派专精：在法术属性上叠加学派 id（1=fire/2=ice/3=lightning/4=holy/5=ender/6=blood/7=evocation/8=eldritch） */
public class SchoolFocusAffix extends SpellAffix {
    public static final Codec<SchoolFocusAffix> C = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("modifier").forGetter(a -> a.mod),
            Codec.unboundedMap(Codec.STRING, Fn.C).fieldOf("values").forGetter(a -> a.vals),
            Codec.STRING.listOf().xmap(Set::copyOf, s -> s.stream().toList()).fieldOf("types").forGetter(a -> a.types)
    ).apply(i, SchoolFocusAffix::new));

    public SchoolFocusAffix(String m, Map<String, Fn> v, Set<String> t) { super(m, v, t, AffixType.STAT); }

    @Override
    public ReforgeCache.Data contribute(int baseValue) {
        // baseValue 表示学派 id（1..8）。0 表示无学派专精
        return new ReforgeCache.Data(1, 1, 1, 1, 0, 1, 1, baseValue);
    }

    @Override
    protected Codec<? extends SpellAffix> getSelfCodec() { return C; }
}
