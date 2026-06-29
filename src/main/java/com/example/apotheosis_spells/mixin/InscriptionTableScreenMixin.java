package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
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

    private static final String PREFIX = "[InscriptionTable]";
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

            ReforgeCache.Data data = ReforgeCache.getFromSpellBook(bookStack, physicalIndex);
            SpellSlot spellSlot = getSpellSlot(self, selectedIndex);
            if (spellSlot == null) {
                SpellCastHooks.clear();
                return;
            }

            int spellLevel = spellSlot.getLevel();
            SpellCastHooks.set(new SpellCastHooks.Context(bookStack, player, physicalIndex, spellLevel, data, spellSlot.spellData()));

            ApotheosisSpells.LOGGER.info("{} renderLorePage ENTER: selectedIndex={}, physicalIndex={}, spellLevel={}, data={}",
                    PREFIX, selectedIndex, physicalIndex, spellLevel, data);

        } catch (Exception e) {
            ApotheosisSpells.LOGGER.error("{} Exception: {}", PREFIX, e.getMessage());
            SpellCastHooks.clear();
        }
    }

    @Inject(method = "renderLorePage", at = @At("RETURN"))
    private void onRenderLorePageReturn(net.minecraft.client.gui.GuiGraphics guiHelper, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        SpellCastHooks.clear();
    }

    /**
     * 拦截 getLevelFor：返回 boosted 等级
     */
    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getLevelFor(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private int redirectGetLevelFor(AbstractSpell spell, int level, LivingEntity caster) {
        int result = spell.getLevelFor(level, caster);
        var ctx = SpellCastHooks.get();
        int boosted = result;
        if (ctx != null && ctx.data() != null && ctx.data().lvl() > 0) {
            boosted = result + ctx.data().lvl();
            ApotheosisSpells.LOGGER.info("{} getLevelFor: spell={}, originLevel={}, afterAffinity={}, boosted={}, d.lvl={}",
                    PREFIX, spell.getSpellResource(), level, result, boosted, ctx.data().lvl());
        }
        return boosted;
    }

    /**
     * 拦截 getUniqueInfo：把 spellLevel boost 后传入
     */
    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getUniqueInfo(ILnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;"))
    private List<MutableComponent> redirectGetUniqueInfo(AbstractSpell spell, int spellLevel, LivingEntity caster) {
        var ctx = SpellCastHooks.get();
        int boostedLevel = spellLevel;
        if (ctx != null && ctx.data() != null && ctx.data().lvl() > 0) {
            boostedLevel = spellLevel + ctx.data().lvl();
            ApotheosisSpells.LOGGER.info("{} getUniqueInfo: spell={}, originLevel={}, boosted={}, d.lvl={}",
                    PREFIX, spell.getSpellResource(), spellLevel, boostedLevel, ctx.data().lvl());
        } else {
            ApotheosisSpells.LOGGER.info("{} getUniqueInfo: spell={}, level={}, no d.lvl",
                    PREFIX, spell.getSpellResource(), spellLevel);
        }
        return spell.getUniqueInfo(boostedLevel, caster);
    }

    /**
     * 拦截 getManaCost：先把 spellLevel boost，再乘 d.mana()
     */
    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private int redirectGetManaCost(AbstractSpell spell, int level) {
        var ctx = SpellCastHooks.get();
        int boostedLevel = level;
        if (ctx != null && ctx.data() != null && ctx.data().lvl() > 0) {
            boostedLevel = level + ctx.data().lvl();
        }
        int base = spell.getManaCost(boostedLevel);
        if (ctx == null || ctx.data() == null || ctx.data().mana() == 1f) {
            ApotheosisSpells.LOGGER.info("{} getManaCost: spell={}, originLevel={}, boosted={}, base={}, d.mana=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), level, boostedLevel, base);
            return base;
        }
        int result = Math.max(0, Math.round(base * ctx.data().mana()));
        ApotheosisSpells.LOGGER.info("{} getManaCost: spell={}, originLevel={}, boosted={}, base={}, d.mana={}, final={}",
                PREFIX, spell.getSpellResource(), level, boostedLevel, base, ctx.data().mana(), result);
        return result;
    }

    /**
     * 拦截 getSpellCooldown：直接应用 d.cd()
     */
    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellCooldown()I"))
    private int redirectGetSpellCooldown(AbstractSpell spell) {
        var ctx = SpellCastHooks.get();
        int base = spell.getSpellCooldown();
        if (ctx == null || ctx.data() == null || ctx.data().cd() == 1f) {
            ApotheosisSpells.LOGGER.info("{} getSpellCooldown: spell={}, base={}, d.cd=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), base);
            return base;
        }
        int result = Math.max(0, Math.round(base * ctx.data().cd()));
        ApotheosisSpells.LOGGER.info("{} getSpellCooldown: spell={}, base={}, d.cd={}, final={}",
                PREFIX, spell.getSpellResource(), base, ctx.data().cd(), result);
        return result;
    }

    /**
     * 拦截 getEffectiveCastTime：先把 spellLevel boost，再应用 d.cast()
     */
    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private int redirectGetEffectiveCastTime(AbstractSpell spell, int spellLevel, LivingEntity entity) {
        var ctx = SpellCastHooks.get();
        int boostedLevel = spellLevel;
        if (ctx != null && ctx.data() != null && ctx.data().lvl() > 0) {
            boostedLevel = spellLevel + ctx.data().lvl();
        }
        int base = spell.getEffectiveCastTime(boostedLevel, entity);
        if (ctx == null || ctx.data() == null || ctx.data().cast() == 1f) {
            ApotheosisSpells.LOGGER.info("{} getEffectiveCastTime: spell={}, originLevel={}, boosted={}, base={}, d.cast=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), spellLevel, boostedLevel, base);
            return base;
        }
        int result = Math.max(0, Math.round(base * ctx.data().cast()));
        ApotheosisSpells.LOGGER.info("{} getEffectiveCastTime: spell={}, originLevel={}, boosted={}, base={}, d.cast={}, final={}",
                PREFIX, spell.getSpellResource(), spellLevel, boostedLevel, base, ctx.data().cast(), result);
        return result;
    }

    /**
     * 拦截 getLevelComponenet：显示 boosted 等级带 +/-diff
     */
    @Redirect(method = "renderLorePage", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/util/TooltipsUtils;getLevelComponenet(Lio/redspace/ironsspellbooks/api/spells/SpellData;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent redirectGetLevelComponenet(io.redspace.ironsspellbooks.api.spells.SpellData spellData, LivingEntity caster) {
        int stored = spellData.getLevel();
        int level = spellData.getSpell().getLevelFor(stored, caster);
        var ctx = SpellCastHooks.get();
        int diff = 0;
        if (ctx != null && ctx.data() != null && ctx.data().lvl() > 0) {
            level += ctx.data().lvl();
            diff = ctx.data().lvl();
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
