package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
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

    private static final String PREFIX = "[SpellWheel]";

    @Unique
    private static final Map<ResourceLocation, ReforgeCache.Data> SPELL_DATA_MAP = new HashMap<>();

    /**
     * 拦截 getLevelFor：返回 boosted 等级 = 原等级 + d.lvl()
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getLevelFor(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private int redirectGetLevelFor(AbstractSpell spell, int level, LivingEntity caster) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        int result = spell.getLevelFor(level, caster);
        int boosted = result;
        if (d != null && d.lvl() > 0) {
            boosted = result + d.lvl();
            ApotheosisSpells.LOGGER.info("{} getLevelFor: spell={}, originLevel={}, afterAffinity={}, boosted={}, d.lvl={}",
                    PREFIX, spell.getSpellResource(), level, result, boosted, d.lvl());
        }
        return boosted;
    }

    /**
     * 拦截 getSpellPower：先用 boosted 等级计算，再乘 d.dmg()
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private float redirectGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        float base = spell.getSpellPower(spellLevel, source);
        if (d == null || d.dmg() == 1f) {
            ApotheosisSpells.LOGGER.info("{} getSpellPower: spell={}, level={}, base={}, d.dmg=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), spellLevel, base);
            return base;
        }
        float result = base * d.dmg();
        ApotheosisSpells.LOGGER.info("{} getSpellPower: spell={}, level={}, base={}, d.dmg={}, final={}",
                PREFIX, spell.getSpellResource(), spellLevel, base, d.dmg(), result);
        return result;
    }

    /**
     * 拦截 getManaCost：先用 boosted 等级计算，再乘 d.mana()
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private int redirectGetManaCost(AbstractSpell spell, int level) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        int base = spell.getManaCost(level);
        if (d == null || d.mana() == 1f) {
            ApotheosisSpells.LOGGER.info("{} getManaCost: spell={}, level={}, base={}, d.mana=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), level, base);
            return base;
        }
        int result = Math.max(0, Math.round(base * d.mana()));
        ApotheosisSpells.LOGGER.info("{} getManaCost: spell={}, level={}, base={}, d.mana={}, final={}",
                PREFIX, spell.getSpellResource(), level, base, d.mana(), result);
        return result;
    }

    /**
     * 拦截 getEffectiveCastTime：直接应用 d.cast()
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private int redirectGetEffectiveCastTime(AbstractSpell spell, int spellLevel, LivingEntity entity) {
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spell.getSpellResource());
        int base = spell.getEffectiveCastTime(spellLevel, entity);
        if (d == null || d.cast() == 1f) {
            ApotheosisSpells.LOGGER.info("{} getEffectiveCastTime: spell={}, level={}, base={}, d.cast=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), spellLevel, base);
            return base;
        }
        int result = Math.max(0, Math.round(base * d.cast()));
        ApotheosisSpells.LOGGER.info("{} getEffectiveCastTime: spell={}, level={}, base={}, d.cast={}, final={}",
                PREFIX, spell.getSpellResource(), spellLevel, base, d.cast(), result);
        return result;
    }

    /**
     * 拦截 getLevelComponenet：显示 boosted 等级带 +/-diff
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/util/TooltipsUtils;getLevelComponenet(Lio/redspace/ironsspellbooks/api/spells/SpellData;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent redirectGetLevelComponenet(SpellData spellData, LivingEntity caster) {
        int stored = spellData.getLevel();
        int level = spellData.getSpell().getLevelFor(stored, caster);
        ReforgeCache.Data d = SPELL_DATA_MAP.get(spellData.getSpell().getSpellResource());
        int diff = 0;
        if (d != null && d.lvl() > 0) {
            level += d.lvl();
            diff = d.lvl();
        }
        int diffFromStored = level - stored;
        MutableComponent result;
        if (diffFromStored > 0) {
            result = net.minecraft.network.chat.Component.literal(level + " (+" + diffFromStored + ")");
            ApotheosisSpells.LOGGER.info("{} getLevelComponenet: spell={}, stored={}, afterAffinity={}, d.lvl={}, display={}",
                    PREFIX, spellData.getSpell().getSpellResource(), stored, level - diff, diff, level + " (+" + diffFromStored + ")");
        } else if (diffFromStored < 0) {
            result = net.minecraft.network.chat.Component.literal(level + " (" + diffFromStored + ")");
            ApotheosisSpells.LOGGER.info("{} getLevelComponenet: spell={}, stored={}, afterAffinity={}, d.lvl={}, display={}",
                    PREFIX, spellData.getSpell().getSpellResource(), stored, level - diff, diff, level + " (" + diffFromStored + ")");
        } else {
            result = net.minecraft.network.chat.Component.literal(String.valueOf(level));
            ApotheosisSpells.LOGGER.info("{} getLevelComponenet: spell={}, stored={}, level={}, no d.lvl",
                    PREFIX, spellData.getSpell().getSpellResource(), stored, level);
        }
        return result;
    }

    /**
     * 拦截 getUniqueInfo
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getUniqueInfo(ILnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;"))
    private List<MutableComponent> redirectGetUniqueInfo(AbstractSpell spell, int spellLevel, LivingEntity caster) {
        ApotheosisSpells.LOGGER.info("{} getUniqueInfo: spell={}, level={}", PREFIX, spell.getSpellResource(), spellLevel);
        return spell.getUniqueInfo(spellLevel, caster);
    }

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

        ApotheosisSpells.LOGGER.info("{} render HEAD: player={}, bookStack={}", PREFIX, player.getName().getString(), bookStack.getItem().getClass().getSimpleName());

        for (var spellSlot : container.getActiveSpells()) {
            AbstractSpell spell = spellSlot.spellData().getSpell();
            if (spell != null) {
                ResourceLocation spellId = spell.getSpellResource();
                ReforgeCache.Data d = ReforgeCache.getFromSpellBook(bookStack, spellSlot.index());
                if (d != null) {
                    SPELL_DATA_MAP.put(spellId, d);
                    ApotheosisSpells.LOGGER.info("{}   spell: {}, d={}", PREFIX, spellId, d);
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
                ApotheosisSpells.LOGGER.info("{}   selected: {}, d={}", PREFIX, cachedSpell.getSpellResource(), d);
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(CallbackInfo ci) {
        SPELL_DATA_MAP.clear();
        SpellCastHooks.clear();
    }
}
