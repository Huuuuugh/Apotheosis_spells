package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.handler.SpellCastHooks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 范围(radius)词缀在火球术上生效（其 getRadius(int,LivingEntity) 返回 int，单列）。
 * getRadius 同时驱动 tooltip 范围数值与实际爆炸半径(setExplosionRadius)，hook 返回值即可两者一起缩放。
 * 见 {@link SpellRadiusFloatMixin}（返回 float 的其余法术）。
 */
@Mixin(value = io.redspace.ironsspellbooks.spells.fire.FireballSpell.class, remap = false)
public class SpellRadiusIntMixin {

    @Inject(method = "getRadius", at = @At("RETURN"), cancellable = true)
    private void apoth_scaleRadius(int spellLevel, LivingEntity caster, CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().radius() == 1f) return;
        cir.setReturnValue(Math.max(0, Math.round(cir.getReturnValueI() * ctx.data().radius())));
    }
}
