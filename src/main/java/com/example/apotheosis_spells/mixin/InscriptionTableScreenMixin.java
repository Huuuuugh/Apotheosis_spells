package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.ReforgedSpellCalculator;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(value = InscriptionTableScreen.class, remap = false)
public class InscriptionTableScreenMixin {

    private static final int SPELLBOOK_SLOT = 36 + 0;

    @Inject(method = "renderLorePage", at = @At("HEAD"))
    private void onRenderLorePageHead(net.minecraft.client.gui.GuiGraphics guiHelper, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        SpellCastHooks.clear();
        try {
            InscriptionTableScreen self = (InscriptionTableScreen) (Object) this;
            InscriptionTableMenu menu = (InscriptionTableMenu) self.getMenu();

            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            ItemStack bookStack = menu.slots.get(SPELLBOOK_SLOT).getItem();
            if (bookStack.isEmpty() || !(bookStack.getItem() instanceof SpellBook)) {
                SpellCastHooks.clear();
                return;
            }

            int selectedIndex = getSelectedSpellIndex(self);
            if (selectedIndex < 0) {
                SpellCastHooks.clear();
                return;
            }

            int physicalIndex = getPhysicalIndex(self, selectedIndex);
            if (physicalIndex < 0) {
                SpellCastHooks.clear();
                return;
            }

            ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromSpellSlot(bookStack, physicalIndex);
            ReforgeCache.Data data = calc != null ? calc.getData() : ReforgeCache.Data.DEF;
            SpellSlot spellSlot = getSpellSlot(self, selectedIndex);
            if (spellSlot == null) {
                SpellCastHooks.clear();
                return;
            }

            SpellCastHooks.set(new SpellCastHooks.Context(bookStack, player, physicalIndex, spellSlot.getLevel(), data, spellSlot.spellData()));

        } catch (Exception e) {
            SpellCastHooks.clear();
        }
    }

    @Inject(method = "renderLorePage", at = @At("RETURN"))
    private void onRenderLorePageReturn(net.minecraft.client.gui.GuiGraphics guiHelper, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        SpellCastHooks.clear();
    }

    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private float redirectGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) {
            return spell.getSpellPower(spellLevel, source);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromSpellSlot(ctx.stack(), ctx.spellSlotIndex());
        if (calc == null) {
            return spell.getSpellPower(spellLevel, source);
        }
        return calc.getSpellPower(spellLevel, (LivingEntity) source);
    }

    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private int redirectGetManaCost(AbstractSpell spell, int level) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) {
            return spell.getManaCost(level);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromSpellSlot(ctx.stack(), ctx.spellSlotIndex());
        if (calc == null) {
            return spell.getManaCost(level);
        }
        return calc.getManaCost(level);
    }

    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellCooldown()I"))
    private int redirectGetSpellCooldown(AbstractSpell spell) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) {
            return spell.getSpellCooldown();
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromSpellSlot(ctx.stack(), ctx.spellSlotIndex());
        if (calc == null) {
            return spell.getSpellCooldown();
        }
        return calc.getSpellCooldown();
    }

    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private int redirectGetEffectiveCastTime(AbstractSpell spell, int spellLevel, LivingEntity entity) {
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().isDefault()) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromSpellSlot(ctx.stack(), ctx.spellSlotIndex());
        if (calc == null) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        return calc.getEffectiveCastTime(spellLevel, entity);
    }

    private static int getSelectedSpellIndex(InscriptionTableScreen screen) {
        try {
            Field field = screen.getClass().getDeclaredField("selectedSpellIndex");
            field.setAccessible(true);
            Object val = field.get(screen);
            if (val instanceof Integer) {
                return (Integer) val;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static int getPhysicalIndex(InscriptionTableScreen screen, int selectedIndex) {
        try {
            SpellSlot spellSlot = getSpellSlot(screen, selectedIndex);
            if (spellSlot == null) return -1;
            return spellSlot.index();
        } catch (Exception e) {
            return -1;
        }
    }

    private static SpellSlot getSpellSlot(InscriptionTableScreen screen, int selectedIndex) {
        try {
            Field spellSlotsField = screen.getClass().getDeclaredField("spellSlots");
            spellSlotsField.setAccessible(true);
            List<?> spellSlots = (List<?>) spellSlotsField.get(screen);
            if (selectedIndex < 0 || selectedIndex >= spellSlots.size()) return null;

            Object spellSlotInfo = spellSlots.get(selectedIndex);
            Field spellSlotField = spellSlotInfo.getClass().getDeclaredField("spellSlot");
            spellSlotField.setAccessible(true);
            return (SpellSlot) spellSlotField.get(spellSlotInfo);
        } catch (Exception e) {
            return null;
        }
    }
}
