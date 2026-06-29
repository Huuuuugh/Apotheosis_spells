package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractSpell.class, remap = false)
public class SpellCooldownMixin {

    @Inject(method = "getSpellCooldown", at = @At("RETURN"), cancellable = true)
    private void onGetSpellCooldown(CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        int original = cir.getReturnValue();
        ApotheosisSpells.LOGGER.info("[SpellCooldownMixin] ctx={}, isDefault={}, cd={}, original={}", ctx != null, ctx != null ? ctx.data().isDefault() : "N/A", ctx != null ? ctx.data().cd() : "N/A", original);
        if (ctx == null || ctx.data().isDefault()) return;
        if (ctx.data().cd() != 1f) {
            int modified = Math.max(0, Math.round(original * ctx.data().cd()));
            cir.setReturnValue(modified);
            ApotheosisSpells.LOGGER.info("[SpellCooldownMixin] modified={} ({}*{})", modified, original, ctx.data().cd());
        }
    }
}
