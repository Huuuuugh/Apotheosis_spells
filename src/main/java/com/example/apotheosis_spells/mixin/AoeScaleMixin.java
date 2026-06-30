package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 范围(radius)/持续时间(duration)词缀在「AoE 实体类法术」上生效。
 *
 * Iron's 没有 AbstractSpell 级的通用 getRadius/getDuration（各法术自定义、签名不一），无法用单一基类注入。
 * 但所有 AoE 云/场/范围实体都继承 {@code AoeEntity}，其 setRadius/setDuration/setEffectDuration 是统一写入点，
 * 且都在法术 onCast / onServerCastTick（已在 CastMixin 扩窗）内被调用，此时 SpellCastHooks ctx 处于施法上下文。
 * 因此在这三个 setter 上按 ctx 的 radius()/duration() 缩放即可覆盖全部 AoE 实体类法术（地震/治疗光环/闪电/
 * 烈焰喷发/回响等）。仅施法时(castContext)施加一次；setRadius 内部仍有引擎 [0,32] 夹取。
 *
 * 覆盖范围：AoeEntity 的所有子类。限制：把半径/时长直接内联进 onCast（不走 AoeEntity）的少数法术，
 * 以及 MobEffect buff 的时长（经 LivingEntity.addEffect），不在本 mixin 覆盖范围内。
 */
@Mixin(value = AoeEntity.class, remap = false)
public class AoeScaleMixin {

    @ModifyVariable(method = "setRadius", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float apoth_scaleRadius(float r) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || !ctx.castContext() || ctx.data() == null || ctx.data().radius() == 1f) return r;
        return r * ctx.data().radius();
    }

    @ModifyVariable(method = "setDuration", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int apoth_scaleDuration(int duration) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || !ctx.castContext() || ctx.data() == null || ctx.data().duration() == 1f) return duration;
        return Math.max(0, Math.round(duration * ctx.data().duration()));
    }

    @ModifyVariable(method = "setEffectDuration", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int apoth_scaleEffectDuration(int duration) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || !ctx.castContext() || ctx.data() == null || ctx.data().duration() == 1f) return duration;
        return Math.max(0, Math.round(duration * ctx.data().duration()));
    }
}
