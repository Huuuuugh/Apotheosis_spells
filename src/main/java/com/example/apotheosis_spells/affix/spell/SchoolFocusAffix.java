package com.example.apotheosis_spells.affix.spell;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.affix.SpellAffix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;

import java.util.Map;
import java.util.Set;

/**
 * 学派专精：values 产出学派 id（按 SchoolRegistry 顺序 1=fire/2=ice/3=lightning/4=holy/5=ender/6=blood/
 * 7=evocation/8=nature/9=eldritch）。对该学派的法术额外施加 +30% 法术强度（schoolBonus=1.30），
 * 由 CastMixin.apoth_spellPower 在「施法/显示的法术学派 == 此学派」时施加（覆盖伤害/治疗等所有 getSpellPower 派生值）。
 */
public class SchoolFocusAffix extends SpellAffix {
    /** 学派专精的法术强度加成（对匹配学派）。固定 1.30 = +30%，需要可改这里或后续做成按稀有度缩放。 */
    private static final float SCHOOL_FOCUS_BONUS = 1.30f;
    public static final Codec<SchoolFocusAffix> C = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("modifier").forGetter(a -> a.mod),
            Codec.unboundedMap(Codec.STRING, Fn.C).fieldOf("values").forGetter(a -> a.vals),
            Codec.STRING.listOf().xmap(Set::copyOf, s -> s.stream().toList()).fieldOf("types").forGetter(a -> a.types)
    ).apply(i, SchoolFocusAffix::new));

    public SchoolFocusAffix(String m, Map<String, Fn> v, Set<String> t) { super(m, v, t, AffixType.POTION); }

    @Override
    public ReforgeCache.Data contribute(int baseValue) {
        // baseValue = 学派 id（1..9）。0 表示无学派专精。带上 schoolBonus，使匹配学派的法术真正获得加成。
        if (baseValue <= 0) return ReforgeCache.Data.DEF;
        return new ReforgeCache.Data(1, 1, 1, 1, 0, 1, 1, baseValue, SCHOOL_FOCUS_BONUS);
    }

    @Override
    protected Codec<? extends SpellAffix> getSelfCodec() { return C; }
}
