package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.ReforgedSpellCalculator;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractSpell.class, remap = false)
public class CastMixin {

    @Inject(method = "attemptInitiateCast", at = @At("HEAD"))
    private void onAttemptInitiateCastHead(ItemStack stack, int spellLevel, Level level,
                                          Player player, CastSource src,
                                          boolean triggerCooldown, String slot,
                                          CallbackInfoReturnable<Boolean> cir) {
        // 在 HEAD 清空（而不是 RETURN），这样 mana 检查用原始值，但 castSpell 时 ctx 仍存在
        SpellCastHooks.clear();
        if (player == null || !(player instanceof ServerPlayer)) return;

        ItemStack castingStack = resolveCastingStack(stack, slot, player);
        if (castingStack.isEmpty()) return;

        int spellSlotIndex = -1;
        ItemStack affixStack = castingStack;
        if (castingStack.getItem() instanceof SpellBook) {
            spellSlotIndex = 0;
        } else if (castingStack.getItem() instanceof Scroll) {
            spellSlotIndex = 0;
        } else {
            ItemStack spellbook = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
            if (spellbook == null || spellbook.isEmpty() || !(spellbook.getItem() instanceof SpellBook)) {
                ItemStack mainHand = player.getMainHandItem();
                if (mainHand.getItem() instanceof SpellBook) {
                    spellbook = mainHand;
                } else {
                    ItemStack offHand = player.getOffhandItem();
                    if (offHand.getItem() instanceof SpellBook) {
                        spellbook = offHand;
                    }
                }
            }
            if (spellbook != null && !spellbook.isEmpty() && spellbook.getItem() instanceof SpellBook) {
                affixStack = spellbook;
                try {
                    spellSlotIndex = ReforgeCache.resolveSelectedSpellIndex(affixStack, player);
                } catch (Exception ignored) {}
            }
        }

        ReforgeCache.Data d = ReforgeCache.Data.DEF;
        if (affixStack.getItem() instanceof SpellBook && ISpellContainer.isSpellContainer(affixStack)) {
            d = ReforgeCache.getFromSpellBook(affixStack, spellSlotIndex);
            if (d.isDefault()) {
                try {
                    int selIdx = ReforgeCache.resolveSelectedSpellIndex(affixStack, player);
                    d = ReforgeCache.getFromSpellBook(affixStack, selIdx);
                } catch (Exception ignored) {}
            }
        } else if (affixStack.getItem() instanceof Scroll) {
            d = ReforgeCache.getFromScroll(affixStack);
        }
        if (d == null) return;

        ApotheosisSpells.LOGGER.info("[CastMixin] attemptInitiateCast: affixStack={}, slot={}, spellLevel={}, d={}",
                affixStack.getItem(), slot, spellLevel, d);

        SpellData castingSpellData = null;
        if (ISpellContainer.isSpellContainer(affixStack)) {
            castingSpellData = ISpellContainer.get(affixStack).getSpellAtIndex(0);
        }
        SpellCastHooks.set(new SpellCastHooks.Context(affixStack, player, spellSlotIndex, spellLevel, d, castingSpellData));
    }

    @Inject(method = "attemptInitiateCast", at = @At("RETURN"))
    private void onAttemptInitiateCastReturn(ItemStack stack, int spellLevel, Level level,
                                          Player player, CastSource src,
                                          boolean triggerCooldown, String slot,
                                          CallbackInfoReturnable<Boolean> cir) {
        // 不要在这里清除！attemptInitiateCast 在客户端运行，castSpell 在服务端运行
        // SpellCastHooks 需要跨两个调用保持有效
    }

    @Inject(method = "castSpell", at = @At("RETURN"))
    private void onCastSpellReturn(Level world, int spellLevel, ServerPlayer serverPlayer,
                                  CastSource castSource, boolean triggerCooldown, CallbackInfo ci) {
        SpellCastHooks.clear();
    }

    @Inject(method = "getSpellPower", at = @At("HEAD"), cancellable = true)
    private void onGetSpellPower(int spellLevel, net.minecraft.world.entity.Entity src, CallbackInfoReturnable<Float> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;
        if (ctx.data().dmg() == 1f && ctx.data().lvl() == 0) return;
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, ctx.data().lvl());
        SpellCastHooks.Context saved = ctx;
        SpellCastHooks.clear();
        try {
            float base = ((AbstractSpell)(Object)this).getSpellPower(boosted, src);
            cir.setReturnValue(ReforgedSpellCalculator.calcModifiedPower(base, ctx.data().dmg()));
        } finally {
            SpellCastHooks.set(saved);
        }
    }

    @Inject(method = "getManaCost", at = @At("HEAD"), cancellable = true)
    private void onGetManaCost(int spellLevel, CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;
        if (ctx.data().mana() == 1f && ctx.data().lvl() == 0) return;
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, ctx.data().lvl());
        SpellCastHooks.Context saved = ctx;
        SpellCastHooks.clear();
        try {
            int base = ((AbstractSpell)(Object)this).getManaCost(boosted);
            cir.setReturnValue(ReforgedSpellCalculator.calcModifiedMana(base, ctx.data().mana()));
        } finally {
            SpellCastHooks.set(saved);
        }
    }

    @Inject(method = "getEffectiveCastTime", at = @At("HEAD"), cancellable = true)
    private void onGetEffectiveCastTime(int spellLevel, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;
        if (ctx.data().cast() == 1f && ctx.data().lvl() == 0) return;
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, ctx.data().lvl());
        SpellCastHooks.Context saved = ctx;
        SpellCastHooks.clear();
        try {
            int base = ((AbstractSpell)(Object)this).getEffectiveCastTime(boosted, entity);
            cir.setReturnValue(ReforgedSpellCalculator.calcModifiedCastTime(base, ctx.data().cast()));
        } finally {
            SpellCastHooks.set(saved);
        }
    }

    @Inject(method = "getLevelFor", at = @At("HEAD"), cancellable = true)
    private void onGetLevelFor(int level, LivingEntity caster, CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        ApotheosisSpells.LOGGER.info("[CastMixin] getLevelFor: level={}, ctx={}", level, ctx != null ? ctx.data() : "null");
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;
        if (ctx.data().lvl() == 0) return;
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(level, ctx.data().lvl());
        ApotheosisSpells.LOGGER.info("[CastMixin] getLevelFor: boosted={}, result={}", boosted, boosted - level);
        cir.setReturnValue(boosted);
    }

    @Inject(method = "getSpellCooldown", at = @At("HEAD"), cancellable = true)
    private void onGetSpellCooldown(CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        ApotheosisSpells.LOGGER.info("[CastMixin] getSpellCooldown: ctx={}", ctx != null ? ctx.data() : "null");
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;
        if (ctx.data().cd() == 1f) return;
        SpellCastHooks.Context saved = ctx;
        SpellCastHooks.clear();
        try {
            int base = ((AbstractSpell)(Object)this).getSpellCooldown();
            int modified = ReforgedSpellCalculator.calcModifiedCooldown(base, ctx.data().cd());
            ApotheosisSpells.LOGGER.info("[CastMixin] getSpellCooldown: base={}, modified={}", base, modified);
            cir.setReturnValue(modified);
        } finally {
            SpellCastHooks.set(saved);
        }
    }

    private static ItemStack resolveCastingStack(ItemStack stack, String slot, Player player) {
        ItemStack castingStack = stack;
        if (castingStack != null && !castingStack.isEmpty()) return castingStack;
        if (slot == null) return ItemStack.EMPTY;
        if (slot.equals(io.redspace.ironsspellbooks.compat.Curios.SPELLBOOK_SLOT)) {
            return io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
        }
        if ("mainhand".equals(slot)) return player.getMainHandItem();
        if ("offhand".equals(slot)) return player.getOffhandItem();
        if ("head".equals(slot)) return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if ("chest".equals(slot)) return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if ("legs".equals(slot)) return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
        if ("feet".equals(slot)) return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        return ItemStack.EMPTY;
    }
}
