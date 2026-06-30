package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import com.example.apotheosis_spells.handler.SpellEffectHandler;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MagicManager.class, remap = false)
public class MagicManagerMixin {

    @Inject(method = "getEffectiveSpellCooldown", at = @At("RETURN"), cancellable = true)
    private static void onGetEffectiveSpellCooldown(AbstractSpell spell, Player player, CastSource castSource, CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        int original = cir.getReturnValue();
        if (ctx == null) {
            ApotheosisSpells.LOGGER.info("[MagicManagerMixin] getEffectiveSpellCooldown: ctx=null, original={}, final={}", original, original);
            return;
        }
        ApotheosisSpells.LOGGER.info("[MagicManagerMixin] getEffectiveSpellCooldown: ctx.data={}, isDefault={}, data.lvl={}, data.cd={}, original={}", ctx.data(), ctx.data().isDefault(), ctx.data().lvl(), ctx.data().cd(), original);
        if (ctx.data().isDefault()) return;
        if (ctx.data().cd() != 1f) {
            int modified = Math.max(0, Math.round(original * ctx.data().cd()));
            cir.setReturnValue(modified);
            ApotheosisSpells.LOGGER.info("[MagicManagerMixin] getEffectiveSpellCooldown: APPLIED cd {} -> {} (data.cd={})", original, modified, ctx.data().cd());
        }
    }

    /**
     * 免冷却词条：addCooldown 是施法时（castSpell 触发冷却）真正写入冷却的唯一入口，仅服务端、不在显示路径，
     * 故在此按几率 roll 取消整次冷却写入最安全（不影响 tooltip/抄写台的冷却显示）。从施法者实时解析特效。
     */
    @Inject(method = "addCooldown", at = @At("HEAD"), cancellable = true)
    private void apoth_cdSkip(ServerPlayer serverPlayer, AbstractSpell spell, CastSource castSource, CallbackInfo ci) {
        if (serverPlayer == null || spell == null) return;
        float chance = SpellEffectHandler.resolveEffectsFor(serverPlayer, spell.getSpellId()).cdSkip();
        if (chance > 0 && serverPlayer.getRandom().nextFloat() < chance) {
            ci.cancel();
        }
    }
}
