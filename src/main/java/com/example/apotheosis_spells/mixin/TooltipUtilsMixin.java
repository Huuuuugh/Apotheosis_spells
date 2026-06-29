package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.ReforgedSpellCalculator;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = TooltipsUtils.class, remap = false)
public class TooltipUtilsMixin {

    @Inject(method = "formatScrollTooltip", at = @At("HEAD"))
    private static void onEnterScrollTooltip(ItemStack stack, Player player,
                                         CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        SpellCastHooks.clear();
        if (stack == null || stack.isEmpty()) return;
        if (!(stack.getItem() instanceof Scroll)) return;

        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(stack, player);
        ReforgeCache.Data data = calc != null ? calc.getData() : ReforgeCache.Data.DEF;
        SpellData scrollSpellData = io.redspace.ironsspellbooks.api.spells.ISpellContainer.get(stack).getSpellAtIndex(0);
        SpellCastHooks.set(new SpellCastHooks.Context(stack, player, 0, scrollSpellData.getLevel(), data, scrollSpellData));
    }

    @Inject(method = "formatScrollTooltip", at = @At("RETURN"))
    private static void onExitScrollTooltip(ItemStack stack, Player player,
                                        CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        SpellCastHooks.clear();
    }

    @Inject(method = "formatActiveSpellTooltip", at = @At("HEAD"))
    private static void onEnterActiveTooltip(ItemStack stack, SpellData spellData, CastSource castSource,
                                         LocalPlayer player,
                                         CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        SpellCastHooks.clear();
        if (spellData == null || spellData == SpellData.EMPTY) return;

        ItemStack bookStack = stack;
        if (bookStack == null || bookStack.isEmpty()) {
            bookStack = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
        }

        ReforgedSpellCalculator calc = null;
        if (bookStack != null && !bookStack.isEmpty() && bookStack.getItem() instanceof SpellBook) {
            calc = ReforgedSpellCalculator.fromStack(bookStack, player);
        }

        ReforgeCache.Data data = calc != null ? calc.getData() : ReforgeCache.Data.DEF;
        int slotIndex = calc != null ? calc.getSpellSlotIndex() : -1;
        SpellCastHooks.set(new SpellCastHooks.Context(bookStack, player, slotIndex, spellData.getLevel(), data, spellData));
    }

    @Inject(method = "formatActiveSpellTooltip", at = @At("RETURN"))
    private static void onExitActiveTooltip(ItemStack stack, SpellData spellData, CastSource castSource,
                                        LocalPlayer player,
                                        CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        SpellCastHooks.clear();
    }

    /**
     * 拦截 getSpellPower：使用 ReforgedSpellCalculator 计算
     */
    @Inject(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"),
            cancellable = true)
    private static void onGetSpellPowerScroll(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source,
                                            CallbackInfoReturnable<Float> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;

        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) return;

        float result = calc.getSpellPower(spellLevel, (LivingEntity) source);
        cir.setReturnValue(result);
    }

    /**
     * 拦截 getManaCost：使用 ReforgedSpellCalculator 计算
     */
    @Inject(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"),
            cancellable = true)
    private static void onGetManaCostScroll(AbstractSpell spell, int level,
                                           CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;

        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) return;

        int result = calc.getManaCost(level);
        cir.setReturnValue(result);
    }

    /**
     * 拦截 getEffectiveCastTime：使用 ReforgedSpellCalculator 计算
     */
    @Inject(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"),
            cancellable = true)
    private static void onGetEffectiveCastTimeScroll(AbstractSpell spell, int spellLevel, LivingEntity entity,
                                                   CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;

        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) return;

        int result = calc.getEffectiveCastTime(spellLevel, entity);
        cir.setReturnValue(result);
    }

    // ==================== formatActiveSpellTooltip ====================

    @Inject(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"),
            cancellable = true)
    private static void onGetSpellPowerActive(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source,
                                            CallbackInfoReturnable<Float> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;

        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) return;

        float result = calc.getSpellPower(spellLevel, (LivingEntity) source);
        cir.setReturnValue(result);
    }

    @Inject(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"),
            cancellable = true)
    private static void onGetManaCostActive(AbstractSpell spell, int level,
                                           CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;

        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) return;

        int result = calc.getManaCost(level);
        cir.setReturnValue(result);
    }

    @Inject(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"),
            cancellable = true)
    private static void onGetEffectiveCastTimeActive(AbstractSpell spell, int spellLevel, LivingEntity entity,
                                                   CallbackInfoReturnable<Integer> cir) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) return;

        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) return;

        int result = calc.getEffectiveCastTime(spellLevel, entity);
        cir.setReturnValue(result);
    }
}
