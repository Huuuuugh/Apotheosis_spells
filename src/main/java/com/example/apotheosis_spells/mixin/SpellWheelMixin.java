package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.api.ReforgedSpellCalculator;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
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
import java.util.List;
import java.util.Map;

@Mixin(value = SpellWheelOverlay.class, remap = false)
public class SpellWheelMixin {

    @Unique
    private static final Map<ResourceLocation, ReforgeCache.Data> SPELL_DATA_MAP = new HashMap<>();

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(CallbackInfo ci) {
        SpellCastHooks.clear();
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

        SpellSelectionManager ssm = new SpellSelectionManager(player);
        var sel = ssm.getSelection();
        if (sel != null) {
            AbstractSpell cachedSpell = sel.spellData.getSpell();
            if (cachedSpell != null) {
                ReforgeCache.Data d = SPELL_DATA_MAP.get(cachedSpell.getSpellResource());
                if (d == null) d = ReforgeCache.Data.DEF;
                SpellCastHooks.set(new SpellCastHooks.Context(bookStack, player, -1, sel.spellData.getLevel(), d, sel.spellData));
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(CallbackInfo ci) {
        SPELL_DATA_MAP.clear();
        SpellCastHooks.clear();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private float redirectGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        if (d == null || d.isDefault()) {
            return spell.getSpellPower(spellLevel, source);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromSpellSlot(
                io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(Minecraft.getInstance().player), spellSlotIndex(d));
        if (calc == null) {
            return spell.getSpellPower(spellLevel, source);
        }
        return calc.getSpellPower(spellLevel, (LivingEntity) source);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private int redirectGetManaCost(AbstractSpell spell, int level) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        if (d == null || d.isDefault()) {
            return spell.getManaCost(level);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromSpellSlot(
                io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(Minecraft.getInstance().player), spellSlotIndex(d));
        if (calc == null) {
            return spell.getManaCost(level);
        }
        return calc.getManaCost(level);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private int redirectGetEffectiveCastTime(AbstractSpell spell, int spellLevel, LivingEntity entity) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        if (d == null || d.isDefault()) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        ReforgedSpellCalculator calc = ReforgedSpellCalculator.fromSpellSlot(
                io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(Minecraft.getInstance().player), spellSlotIndex(d));
        if (calc == null) {
            return spell.getEffectiveCastTime(spellLevel, entity);
        }
        return calc.getEffectiveCastTime(spellLevel, entity);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getLevelFor(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private int redirectGetLevelFor(AbstractSpell spell, int level, LivingEntity caster) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        int result = spell.getLevelFor(level, caster);
        if (d == null || d.lvl() <= 0) return result;
        return result + d.lvl();
    }

    private int spellSlotIndex(ReforgeCache.Data d) {
        // 查找 d 在 SPELL_DATA_MAP 中的槽位
        for (Map.Entry<ResourceLocation, ReforgeCache.Data> entry : SPELL_DATA_MAP.entrySet()) {
            if (entry.getValue() == d) {
                // 需要从 player 的 spellbook 解析
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    return ReforgeCache.resolveSelectedSpellIndex(
                            io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(player), player);
                }
            }
        }
        return -1;
    }
}
