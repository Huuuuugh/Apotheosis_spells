package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.ReforgedSpellCalculator;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = TooltipsUtils.class, remap = false)
public class TooltipUtilsMixin {

    @Unique
    private static ItemStack apotheosis_spells_scrollStack = ItemStack.EMPTY;

    @Unique
    private static ReforgeCache.Data apotheosis_spells_scrollData = ReforgeCache.Data.DEF;

    @Unique
    private static ItemStack apotheosis_spells_bookStack = ItemStack.EMPTY;

    @Unique
    private static ReforgeCache.Data apotheosis_spells_bookData = ReforgeCache.Data.DEF;

    @Unique
    private static int apotheosis_spells_spellSlotIndex = -1;

    @Inject(method = "formatScrollTooltip", at = @At("HEAD"))
    private static void onEnterScrollTooltip(ItemStack stack, Player player,
                                         CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        apotheosis_spells_scrollStack = ItemStack.EMPTY;
        apotheosis_spells_scrollData = ReforgeCache.Data.DEF;

        if (stack == null || stack.isEmpty()) return;
        if (!(stack.getItem() instanceof Scroll)) return;

        apotheosis_spells_scrollStack = stack;
        apotheosis_spells_scrollData = ReforgeCache.getFromScroll(stack);
    }

    @Inject(method = "formatScrollTooltip", at = @At("RETURN"))
    private static void onExitScrollTooltip(ItemStack stack, Player player,
                                        CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        apotheosis_spells_scrollStack = ItemStack.EMPTY;
        apotheosis_spells_scrollData = ReforgeCache.Data.DEF;
    }

    @Inject(method = "formatActiveSpellTooltip", at = @At("HEAD"))
    private static void onEnterActiveTooltip(ItemStack stack, SpellData spellData, CastSource castSource,
                                         LocalPlayer player,
                                         CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        apotheosis_spells_bookStack = ItemStack.EMPTY;
        apotheosis_spells_bookData = ReforgeCache.Data.DEF;
        apotheosis_spells_spellSlotIndex = -1;

        if (spellData == null || spellData == SpellData.EMPTY) return;

        ItemStack bookStack = stack;
        if (bookStack == null || bookStack.isEmpty()) {
            bookStack = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
        }

        if (bookStack != null && !bookStack.isEmpty() && bookStack.getItem() instanceof SpellBook) {
            try {
                apotheosis_spells_spellSlotIndex = ReforgeCache.resolveSelectedSpellIndex(bookStack, player);
            } catch (Exception e) {
                apotheosis_spells_spellSlotIndex = -1;
            }
            apotheosis_spells_bookStack = bookStack;
            apotheosis_spells_bookData = ReforgeCache.getFromSpellBook(bookStack, apotheosis_spells_spellSlotIndex);
        }
    }

    @Inject(method = "formatActiveSpellTooltip", at = @At("RETURN"))
    private static void onExitActiveTooltip(ItemStack stack, SpellData spellData, CastSource castSource,
                                        LocalPlayer player,
                                        CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        apotheosis_spells_bookStack = ItemStack.EMPTY;
        apotheosis_spells_bookData = ReforgeCache.Data.DEF;
        apotheosis_spells_spellSlotIndex = -1;
    }

    // ==================== formatScrollTooltip 重定向 ====================

    @Redirect(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private static float redirectGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        if (apotheosis_spells_scrollData == null || apotheosis_spells_scrollData.isDefault()) {
            return spell.getSpellPower(spellLevel, source);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, apotheosis_spells_scrollData.lvl());
        float base = spell.getSpellPower(boosted, source);
        return ReforgedSpellCalculator.calcModifiedPower(base, apotheosis_spells_scrollData.dmg());
    }

    @Redirect(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private static int redirectGetManaCost(AbstractSpell spell, int level) {
        if (apotheosis_spells_scrollData == null || apotheosis_spells_scrollData.isDefault()) {
            return spell.getManaCost(level);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(level, apotheosis_spells_scrollData.lvl());
        int base = spell.getManaCost(boosted);
        return ReforgedSpellCalculator.calcModifiedMana(base, apotheosis_spells_scrollData.mana());
    }

    @Redirect(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private static int redirectGetEffectiveCastTime(AbstractSpell spell, int spellLevel, LivingEntity entity) {
        if (apotheosis_spells_scrollData == null || apotheosis_spells_scrollData.isDefault()) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, apotheosis_spells_scrollData.lvl());
        int base = spell.getEffectiveCastTime(boosted, entity);
        return ReforgedSpellCalculator.calcModifiedCastTime(base, apotheosis_spells_scrollData.cast());
    }

    // ==================== formatActiveSpellTooltip 重定向 ====================

    @Redirect(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private static float redirectActiveGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        if (apotheosis_spells_bookData == null || apotheosis_spells_bookData.isDefault()) {
            return spell.getSpellPower(spellLevel, source);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, apotheosis_spells_bookData.lvl());
        float base = spell.getSpellPower(boosted, source);
        return ReforgedSpellCalculator.calcModifiedPower(base, apotheosis_spells_bookData.dmg());
    }

    @Redirect(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private static int redirectActiveGetManaCost(AbstractSpell spell, int level) {
        if (apotheosis_spells_bookData == null || apotheosis_spells_bookData.isDefault()) {
            return spell.getManaCost(level);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(level, apotheosis_spells_bookData.lvl());
        int base = spell.getManaCost(boosted);
        return ReforgedSpellCalculator.calcModifiedMana(base, apotheosis_spells_bookData.mana());
    }

    @Redirect(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private static int redirectActiveGetEffectiveCastTime(AbstractSpell spell, int spellLevel, LivingEntity entity) {
        if (apotheosis_spells_bookData == null || apotheosis_spells_bookData.isDefault()) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, apotheosis_spells_bookData.lvl());
        int base = spell.getEffectiveCastTime(boosted, entity);
        return ReforgedSpellCalculator.calcModifiedCastTime(base, apotheosis_spells_bookData.cast());
    }
}
