package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.handler.SpellCastHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 范围词缀作用于虹吸射线的 <b>静态</b> getRange(int)（返回 float，单参）——射线长度，在 onServerCastTick 内使用
 * （CastMixin 已扩展该窗口的 ctx）。
 */
@Mixin(value = io.redspace.ironsspellbooks.spells.blood.RayOfSiphoningSpell.class, remap = false)
public class SpellRangeStatic1ArgMixin {

    @Inject(method = "getRange", at = @At("RETURN"), cancellable = true)
    private static void apoth_scaleRange(int spellLevel, CallbackInfoReturnable<Float> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().radius() == 1f) return;
        cir.setReturnValue(cir.getReturnValueF() * ctx.data().radius());
    }
}
