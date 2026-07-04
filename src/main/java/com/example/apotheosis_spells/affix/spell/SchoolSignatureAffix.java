package com.example.apotheosis_spells.affix.spell;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.Schools;
import com.example.apotheosis_spells.api.SpellEffects;
import com.example.apotheosis_spells.affix.SpellAffix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;

/**
 * 学派签名词条：只在「对应学派的卷轴」上 roll（canApplyTo 读卷轴法术学派比对），施加该学派专属效果
 * （signature=学派 id，signatureValue=数值，由 SpellEffectHandler 按学派 switch 结算）。
 * 9 个学派各一个 JSON，共用本 codec，靠 JSON 的 "school" 字段区分。
 */
public class SchoolSignatureAffix extends SpellAffix {
    public static final Codec<SchoolSignatureAffix> C = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("modifier").forGetter(a -> a.mod),
            Codec.unboundedMap(Codec.STRING, Fn.C).fieldOf("values").forGetter(a -> a.vals),
            Codec.STRING.listOf().xmap(Set::copyOf, s -> s.stream().toList()).fieldOf("types").forGetter(a -> a.types),
            Codec.STRING.fieldOf("school").forGetter(a -> a.school)
    ).apply(i, SchoolSignatureAffix::new));

    private final String school;
    private final int schoolId;

    public SchoolSignatureAffix(String m, Map<String, Fn> v, Set<String> t, String school) {
        super(m, v, t, AffixType.POTION);
        this.school = school;
        this.schoolId = Schools.idFromName(school);
    }

    @Override
    public boolean canApplyTo(ItemStack stack, LootCategory cat, LootRarity rarity) {
        if (!types.contains(cat.getName()) || schoolId == 0 || !vals.containsKey(rarityKey(rarity))) return false;
        try {
            if (!(stack.getItem() instanceof Scroll) || !ISpellContainer.isSpellContainer(stack)) return false;
            SpellData sd = ISpellContainer.get(stack).getSpellAtIndex(0);
            if (sd == null || sd.getSpell() == null) return false;
            ResourceLocation sc = sd.getSpell().getSchoolType().getId();
            return sc != null && sc.equals(Schools.resource(schoolId));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ReforgeCache.Data contribute(int baseValue) { return ReforgeCache.Data.DEF; }

    @Override
    public SpellEffects contributeEffect(int baseValue) {
        if (schoolId == 0 || baseValue == 0) return SpellEffects.NONE;
        return SpellEffects.ofSignature(schoolId, baseValue);
    }

    @Override
    protected Codec<? extends SpellAffix> getSelfCodec() { return C; }
}
