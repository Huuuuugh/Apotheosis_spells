package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.ReforgedSpellCalculator;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(value = InscriptionTableScreen.class, remap = false)
public class InscriptionTableScreenMixin {

    private static final int SPELLBOOK_SLOT = 36 + 0;

    @Unique
    private ItemStack apotheosis_spells_bookStack = ItemStack.EMPTY;

    @Unique
    private ReforgeCache.Data apotheosis_spells_data = ReforgeCache.Data.DEF;

    @Unique
    private int apotheosis_spells_slotIndex = -1;

    @Inject(method = "renderLorePage", at = @At("HEAD"))
    private void onRenderLorePageHead(net.minecraft.client.gui.GuiGraphics guiHelper, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        apotheosis_spells_bookStack = ItemStack.EMPTY;
        apotheosis_spells_data = ReforgeCache.Data.DEF;
        apotheosis_spells_slotIndex = -1;

        try {
            InscriptionTableScreen self = (InscriptionTableScreen) (Object) this;
            InscriptionTableMenu menu = (InscriptionTableMenu) self.getMenu();

            if (Minecraft.getInstance().player == null) return;

            ItemStack bookStack = menu.slots.get(SPELLBOOK_SLOT).getItem();
            if (bookStack.isEmpty() || !(bookStack.getItem() instanceof SpellBook)) return;

            int selectedIndex = getSelectedSpellIndex(self);
            if (selectedIndex < 0) return;

            int physicalIndex = getPhysicalIndex(self, selectedIndex);
            if (physicalIndex < 0) return;

            SpellSlot spellSlot = getSpellSlot(self, selectedIndex);
            if (spellSlot == null) return;

            apotheosis_spells_bookStack = bookStack;
            apotheosis_spells_slotIndex = physicalIndex;
            apotheosis_spells_data = ReforgeCache.getFromSpellBook(bookStack, physicalIndex);
        } catch (Exception ignored) {}
    }

    @Inject(method = "renderLorePage", at = @At("RETURN"))
    private void onRenderLorePageReturn(net.minecraft.client.gui.GuiGraphics guiHelper, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        apotheosis_spells_bookStack = ItemStack.EMPTY;
        apotheosis_spells_data = ReforgeCache.Data.DEF;
        apotheosis_spells_slotIndex = -1;
    }

    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private float redirectGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        if (apotheosis_spells_data == null || apotheosis_spells_data.isDefault()) {
            return spell.getSpellPower(spellLevel, source);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, apotheosis_spells_data.lvl());
        float base = spell.getSpellPower(boosted, source);
        return ReforgedSpellCalculator.calcModifiedPower(base, apotheosis_spells_data.dmg());
    }

    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private int redirectGetManaCost(AbstractSpell spell, int level) {
        if (apotheosis_spells_data == null || apotheosis_spells_data.isDefault()) {
            return spell.getManaCost(level);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(level, apotheosis_spells_data.lvl());
        int base = spell.getManaCost(boosted);
        return ReforgedSpellCalculator.calcModifiedMana(base, apotheosis_spells_data.mana());
    }

    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellCooldown()I"))
    private int redirectGetSpellCooldown(AbstractSpell spell) {
        if (apotheosis_spells_data == null || apotheosis_spells_data.isDefault()) {
            return spell.getSpellCooldown();
        }
        int base = spell.getSpellCooldown();
        return ReforgedSpellCalculator.calcModifiedCooldown(base, apotheosis_spells_data.cd());
    }

    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private int redirectGetEffectiveCastTime(AbstractSpell spell, int spellLevel, LivingEntity entity) {
        if (apotheosis_spells_data == null || apotheosis_spells_data.isDefault()) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, apotheosis_spells_data.lvl());
        int base = spell.getEffectiveCastTime(boosted, entity);
        return ReforgedSpellCalculator.calcModifiedCastTime(base, apotheosis_spells_data.cast());
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
