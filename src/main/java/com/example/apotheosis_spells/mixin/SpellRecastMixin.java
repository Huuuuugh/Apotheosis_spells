package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.handler.SpellEffectHandler;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 连发增幅词条：在 {@link AbstractSpell#getRecastCount(int, LivingEntity)} 返回值上 +N 段。
 * getRecastCount 同时驱动施法实际连发段数与显示，故 hook 返回值即可两者一致；非 recast 类法术该值为 0，+N 无害
 * （Iron's 仅对 recast 机制法术使用此值）。从施法者实时解析特效（不依赖 ThreadLocal）。
 */
@Mixin(value = AbstractSpell.class, remap = false)
public class SpellRecastMixin {

    @Inject(method = "getRecastCount", at = @At("RETURN"), cancellable = true)
    private void apoth_addRecast(int spellLevel, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        int base = cir.getReturnValueI();
        if (base <= 0 || entity == null) return; // 仅对本就支持连发的法术加成
        String spellId = ((AbstractSpell) (Object) this).getSpellId();
        int add = SpellEffectHandler.resolveEffectsFor(entity, spellId).recast();
        if (add != 0) cir.setReturnValue(base + add);
    }
}
