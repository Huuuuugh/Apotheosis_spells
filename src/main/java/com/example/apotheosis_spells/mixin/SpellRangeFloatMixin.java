package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.handler.SpellCastHooks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 范围(radius)词缀同样作用于「射程类」法术的 getRange(int,LivingEntity)（实例方法、返回 float）。
 * getRange 同时驱动 tooltip 与实际作用距离（连锁闪电跳跃距离 / 疾风推击距离等），hook 返回值即可两者一起缩放。
 */
@Mixin(value = {
        io.redspace.ironsspellbooks.spells.lightning.ChainLightningSpell.class,
        io.redspace.ironsspellbooks.spells.evocation.GustSpell.class
}, remap = false)
public class SpellRangeFloatMixin {

    @Inject(method = "getRange", at = @At("RETURN"), cancellable = true)
    private void apoth_scaleRange(int spellLevel, LivingEntity caster, CallbackInfoReturnable<Float> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().radius() == 1f) return;
        cir.setReturnValue(cir.getReturnValueF() * ctx.data().radius());
    }
}
