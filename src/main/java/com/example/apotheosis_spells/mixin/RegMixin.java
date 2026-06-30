package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.affix.spell.CastTimeAffix;
import com.example.apotheosis_spells.affix.spell.CdSkipAffix;
import com.example.apotheosis_spells.affix.spell.CooldownAffix;
import com.example.apotheosis_spells.affix.spell.EchoAffix;
import com.example.apotheosis_spells.affix.spell.ExecuteAffix;
import com.example.apotheosis_spells.affix.spell.LifestealAffix;
import com.example.apotheosis_spells.affix.spell.ManaCostAffix;
import com.example.apotheosis_spells.affix.spell.ManaStealAffix;
import com.example.apotheosis_spells.affix.spell.OverchargeAffix;
import com.example.apotheosis_spells.affix.spell.RecastAffix;
import com.example.apotheosis_spells.affix.spell.SchoolFocusAffix;
import com.example.apotheosis_spells.affix.spell.SpellCritAffix;
import com.example.apotheosis_spells.affix.spell.SpellDurationAffix;
import com.example.apotheosis_spells.affix.spell.SpellLevelAffix;
import com.example.apotheosis_spells.affix.spell.SpellPowerAffix;
import com.example.apotheosis_spells.affix.spell.SpellRadiusAffix;
import com.example.apotheosis_spells.affix.spell.SwiftnessAffix;
import com.example.apotheosis_spells.affix.spell.WardAffix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixRegistry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AffixRegistry.class, remap = false)
public class RegMixin {
    @Inject(method = "registerBuiltinCodecs", at = @At("TAIL"))
    protected void reg(CallbackInfo ci) {
        AffixRegistry reg = (AffixRegistry) (Object) this;
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "spell_power"), SpellPowerAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "mana_cost"), ManaCostAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "cooldown"), CooldownAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "cast_time"), CastTimeAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "spell_level"), SpellLevelAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "spell_radius"), SpellRadiusAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "spell_duration"), SpellDurationAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "school_focus"), SchoolFocusAffix.C);
        // 事件类特效词条（第一梯队：伤害系）
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "lifesteal"), LifestealAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "mana_steal"), ManaStealAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "spell_crit"), SpellCritAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "execute"), ExecuteAffix.C);
        // 事件类特效词条（第二梯队）
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "overcharge"), OverchargeAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "echo"), EchoAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "recast"), RecastAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "cd_skip"), CdSkipAffix.C);
        // 事件类特效词条（第三梯队：生存/功能）
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "ward"), WardAffix.C);
        reg.registerCodec(new ResourceLocation("apotheosis_spells", "swiftness"), SwiftnessAffix.C);
    }
}
