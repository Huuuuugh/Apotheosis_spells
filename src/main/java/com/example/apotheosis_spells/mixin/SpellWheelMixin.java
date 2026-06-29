package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.ReforgedSpellCalculator;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = SpellWheelOverlay.class, remap = false)
public class SpellWheelMixin {

    @Unique
    private static final Map<ResourceLocation, ReforgeCache.Data> SPELL_DATA_MAP = new HashMap<>();

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(CallbackInfo ci) {
        SPELL_DATA_MAP.clear();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        ItemStack bookStack = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player);
        if (bookStack == null || bookStack.isEmpty()) return;
        if (!(bookStack.getItem() instanceof io.redspace.ironsspellbooks.item.SpellBook)) return;

        ISpellContainer container = ISpellContainer.get(bookStack);

        for (var spellSlot : container.getActiveSpells()) {
            AbstractSpell spell = spellSlot.spellData().getSpell();
            if (spell != null) {
                ResourceLocation spellId = spell.getSpellResource();
                ReforgeCache.Data d = ReforgeCache.getFromSpellBook(bookStack, spellSlot.index());
                if (d != null) {
                    SPELL_DATA_MAP.put(spellId, d);
                }
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(CallbackInfo ci) {
        SPELL_DATA_MAP.clear();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private float redirectGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        if (d == null || d.isDefault()) {
            return spell.getSpellPower(spellLevel, source);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, d.lvl());
        float base = spell.getSpellPower(boosted, source);
        return ReforgedSpellCalculator.calcModifiedPower(base, d.dmg());
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private int redirectGetManaCost(AbstractSpell spell, int level) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        if (d == null || d.isDefault()) {
            return spell.getManaCost(level);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(level, d.lvl());
        int base = spell.getManaCost(boosted);
        return ReforgedSpellCalculator.calcModifiedMana(base, d.mana());
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private int redirectGetEffectiveCastTime(AbstractSpell spell, int spellLevel, LivingEntity entity) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        if (d == null || d.isDefault()) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        int boosted = ReforgedSpellCalculator.calcBoostedLevel(spellLevel, d.lvl());
        int base = spell.getEffectiveCastTime(boosted, entity);
        return ReforgedSpellCalculator.calcModifiedCastTime(base, d.cast());
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getLevelFor(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private int redirectGetLevelFor(AbstractSpell spell, int level, LivingEntity caster) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        int result = spell.getLevelFor(level, caster);
        if (d == null || d.lvl() <= 0) return result;
        return result + d.lvl();
    }
}
