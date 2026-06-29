package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.magic.MagicData;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractSpell.class, remap = false)
public class CastMixin {

    private static final String PREFIX = "[CastMixin]";

    /**
     * attemptInitiateCast HEAD：设置 ctx
     */
    @Inject(method = "attemptInitiateCast", at = @At("HEAD"))
    private void onAttemptInitiateCastHead(ItemStack stack, int spellLevel, Level level,
                                          Player player, CastSource src,
                                          boolean triggerCooldown, String slot,
                                          CallbackInfoReturnable<Boolean> cir) {
        SpellCastHooks.clear();
        if (player == null || !(player instanceof ServerPlayer)) return;

        ApotheosisSpells.LOGGER.info("{} attemptInitiateCast HEAD: player={}, spellLevel={}, src={}, slot={}",
                PREFIX, player.getName().getString(), spellLevel, src, slot);

        ItemStack castingStack = resolveCastingStack(stack, slot, player);
        if (castingStack.isEmpty()) {
            ApotheosisSpells.LOGGER.info("{}   castingStack is empty", PREFIX);
            return;
        }

        int spellSlotIndex = -1;
        ItemStack affixStack = castingStack;
        if (castingStack.getItem() instanceof SpellBook) {
            spellSlotIndex = 0;
            ApotheosisSpells.LOGGER.info("{}   source: SpellBook", PREFIX);
        } else if (castingStack.getItem() instanceof Scroll) {
            spellSlotIndex = 0;
            ApotheosisSpells.LOGGER.info("{}   source: Scroll", PREFIX);
        } else {
            ApotheosisSpells.LOGGER.info("{}   source: {}", PREFIX, castingStack.getItem().getClass().getSimpleName());
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

        ApotheosisSpells.LOGGER.info("{}   affixStack: {}, data={}", PREFIX, affixStack.getItem().getClass().getSimpleName(), d);

        if (d == null) return;

        SpellData castingSpellData = null;
        if (ISpellContainer.isSpellContainer(affixStack)) {
            castingSpellData = ISpellContainer.get(affixStack).getSpellAtIndex(0);
        }
        SpellCastHooks.set(new SpellCastHooks.Context(affixStack, player, spellSlotIndex, spellLevel, d, castingSpellData));

        ApotheosisSpells.LOGGER.info("{}   ctx set: spellSlotIndex={}, spellLevel={}, data=lvl={}, dmg={}, mana={}, cd={}, cast={}",
                PREFIX, spellSlotIndex, spellLevel, d.lvl(), d.dmg(), d.mana(), d.cd(), d.cast());
    }

    /**
     * castSpell RETURN：清理 ctx
     */
    @Inject(method = "castSpell", at = @At("RETURN"))
    private void onCastSpellReturn(Level world, int spellLevel, ServerPlayer serverPlayer,
                                   CastSource castSource, boolean triggerCooldown, CallbackInfo ci) {
        ApotheosisSpells.LOGGER.info("{} castSpell RETURN: player={}, spellLevel={}, src={}",
                PREFIX, serverPlayer.getName().getString(), spellLevel, castSource);
        SpellCastHooks.clear();
    }

    /**
     * 拦截 getSpellPower：使用 boosted 等级计算，再乘 d.dmg()
     */
    @Redirect(method = "getSpellPower", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private float redirectGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        var ctx = SpellCastHooks.get();
        int boostedLevel = spellLevel;
        if (ctx != null && ctx.data() != null && ctx.data().lvl() > 0) {
            boostedLevel = spellLevel + ctx.data().lvl();
        }
        float base = spell.getSpellPower(boostedLevel, source);
        if (ctx == null || ctx.data() == null || ctx.data().dmg() == 1f) {
            ApotheosisSpells.LOGGER.info("{} getSpellPower: spell={}, originLevel={}, boosted={}, base={}, d.dmg=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), spellLevel, boostedLevel, base);
            return base;
        }
        float result = base * ctx.data().dmg();
        ApotheosisSpells.LOGGER.info("{} getSpellPower: spell={}, originLevel={}, boosted={}, base={}, d.dmg={}, final={}",
                PREFIX, spell.getSpellResource(), spellLevel, boostedLevel, base, ctx.data().dmg(), result);
        return result;
    }

    /**
     * 拦截 getManaCost：使用 boosted 等级计算，再乘 d.mana()
     */
    @Redirect(method = "getManaCost", at = @At(value = "INVOKE",
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
     * 拦截 getEffectiveCastTime：使用 boosted 等级计算，再应用 d.cast()
     */
    @Redirect(method = "getEffectiveCastTime", at = @At(value = "INVOKE",
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
     * 拦截 getSpellCooldown：直接应用 d.cd()
     */
    @Redirect(method = "getSpellCooldown", at = @At(value = "INVOKE",
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
