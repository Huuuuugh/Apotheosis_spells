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
import org.spongepowered.asm.mixin.injection.Redirect;
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
        int slotIndex = -1;

        if (bookStack == null || bookStack.isEmpty()) {
            bookStack = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
        }

        if (bookStack != null && !bookStack.isEmpty() && bookStack.getItem() instanceof SpellBook) {
            slotIndex = ReforgeCache.resolveSelectedSpellIndex(bookStack, player);
        }

        ReforgeCache.Data data = (bookStack != null && !bookStack.isEmpty())
                ? ReforgeCache.resolveDataFromStack(bookStack, player)
                : ReforgeCache.Data.DEF;
        SpellCastHooks.set(new SpellCastHooks.Context(bookStack, player, slotIndex, spellData.getLevel(), data, spellData));
    }

    @Inject(method = "formatActiveSpellTooltip", at = @At("RETURN"))
    private static void onExitActiveTooltip(ItemStack stack, SpellData spellData, CastSource castSource,
                                        LocalPlayer player,
                                        CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        SpellCastHooks.clear();
    }

    // ==================== formatScrollTooltip 重定向 ====================

    @Redirect(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private static float redirectGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) {
            return spell.getSpellPower(spellLevel, source);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) {
            return spell.getSpellPower(spellLevel, source);
        }
        return calc.getSpellPower(spellLevel, (LivingEntity) source);
    }

    @Redirect(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private static int redirectGetManaCost(AbstractSpell spell, int level) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) {
            return spell.getManaCost(level);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) {
            return spell.getManaCost(level);
        }
        return calc.getManaCost(level);
    }

    @Redirect(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private static int redirectGetEffectiveCastTime(AbstractSpell spell, int spellLevel, LivingEntity entity) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        return calc.getEffectiveCastTime(spellLevel, entity);
    }

    // ==================== formatActiveSpellTooltip 重定向 ====================

    @Redirect(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private static float redirectActiveGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) {
            return spell.getSpellPower(spellLevel, source);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) {
            return spell.getSpellPower(spellLevel, source);
        }
        return calc.getSpellPower(spellLevel, (LivingEntity) source);
    }

    @Redirect(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private static int redirectActiveGetManaCost(AbstractSpell spell, int level) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) {
            return spell.getManaCost(level);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) {
            return spell.getManaCost(level);
        }
        return calc.getManaCost(level);
    }

    @Redirect(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private static int redirectActiveGetEffectiveCastTime(AbstractSpell spell, int spellLevel, LivingEntity entity) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromStack(ctx.stack(), null);
        if (calc == null) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        return calc.getEffectiveCastTime(spellLevel, entity);
    }
}
