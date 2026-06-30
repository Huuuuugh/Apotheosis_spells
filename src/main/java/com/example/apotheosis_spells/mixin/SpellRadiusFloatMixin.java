package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.handler.SpellCastHooks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 范围(radius)词缀在「带 getRadius(int,LivingEntity) 的内联/投射物类法术」上生效（返回 float 的那批）。
 *
 * 这些法术没有走 AoeEntity（故 AoeScaleMixin 覆盖不到），而是各自有一个 getRadius(int,LivingEntity)，
 * 同时被 getUniqueInfo（tooltip 范围数值）与 onCast（实际半径，如 setExplosionRadius / 内联 AABB）使用。
 * 因此 hook getRadius 的返回值即可让「纸面范围数值」与「实际作用范围」一起按 ×radius 缩放。
 * 不按 castContext 区分（显示与施法都要反映）。火球术 getRadius 返回 int，见 {@link SpellRadiusIntMixin}。
 */
@Mixin(value = {
        io.redspace.ironsspellbooks.spells.fire.FireArrowSpell.class,
        io.redspace.ironsspellbooks.spells.fire.HeatSurgeSpell.class,
        io.redspace.ironsspellbooks.spells.fire.MagmaBombSpell.class,
        io.redspace.ironsspellbooks.spells.ice.FrostwaveSpell.class,
        io.redspace.ironsspellbooks.spells.ice.SnowballSpell.class,
        io.redspace.ironsspellbooks.spells.lightning.ShockwaveSpell.class,
        io.redspace.ironsspellbooks.spells.nature.AcidOrbSpell.class
}, remap = false)
public class SpellRadiusFloatMixin {

    @Inject(method = "getRadius", at = @At("RETURN"), cancellable = true)
    private void apoth_scaleRadius(int spellLevel, LivingEntity caster, CallbackInfoReturnable<Float> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().radius() == 1f) return;
        cir.setReturnValue(cir.getReturnValueF() * ctx.data().radius());
    }
}
