package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.ApotheosisSpells;
import com.example.apotheosis_spells.api.ReforgeCache;
import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.minecraft.client.player.LocalPlayer;
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

    private static final String PREFIX = "[TooltipUtils]";

    @Inject(method = "formatScrollTooltip", at = @At("HEAD"))
    private static void onEnterScrollTooltip(ItemStack stack, Player player,
                                             CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        SpellCastHooks.clear();
        if (stack == null || stack.isEmpty()) return;
        if (!(stack.getItem() instanceof Scroll)) return;
        // 创造模式拿出的裸卷轴/其它 mod 给的无 NBT 卷轴没有 SpellContainer，直接跳过防 NPE
        if (!io.redspace.ironsspellbooks.api.spells.ISpellContainer.isSpellContainer(stack)) return;

        ReforgeCache.Data data = ReforgeCache.getFromScroll(stack);
        SpellData scrollSpellData = io.redspace.ironsspellbooks.api.spells.ISpellContainer.get(stack).getSpellAtIndex(0);
        if (scrollSpellData == null || scrollSpellData.getSpell() == null) return;
        SpellCastHooks.set(new SpellCastHooks.Context(stack, player, 0, scrollSpellData.getLevel(), data, scrollSpellData));

        ApotheosisSpells.LOGGER.info("{} formatScrollTooltip ENTER: stack={}, data={}", PREFIX, stack.getItem().getClass().getSimpleName(), data);
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

        ApotheosisSpells.LOGGER.info("{} formatActiveSpellTooltip ENTER: spell={}, data={}", PREFIX, spellData.getSpell().getSpellResource(), data);
    }

    @Inject(method = "formatActiveSpellTooltip", at = @At("RETURN"))
    private static void onExitActiveTooltip(ItemStack stack, SpellData spellData, CastSource castSource,
                                            LocalPlayer player,
                                            CallbackInfoReturnable<List<net.minecraft.network.chat.Component>> cir) {
        SpellCastHooks.clear();
    }

    // ==================== formatScrollTooltip 的重定向 ====================

    /**
     * 拦截 getLevelFor：返回 boosted 等级 = 原等级 + d.lvl()
     */
    @Redirect(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getLevelFor(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private static int redirectGetLevelFor(AbstractSpell spell, int level, net.minecraft.world.entity.LivingEntity caster) {
        int result = spell.getLevelFor(level, caster);
        var ctx = SpellCastHooks.get();
        int boosted = result;
        if (ctx != null && ctx.data() != null && ctx.data().lvl() > 0) {
            boosted = result + ctx.data().lvl();
            ApotheosisSpells.LOGGER.info("{} formatScrollTooltip getLevelFor: spell={}, originLevel={}, afterAffinity={}, boosted={}, d.lvl={}",
                    PREFIX, spell.getSpellResource(), level, result, boosted, ctx.data().lvl());
        }
        return boosted;
    }

    /**
     * 拦截 getSpellPower：先用 boosted 等级计算，再乘 d.dmg()
     */
    @Redirect(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private static float redirectGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        // ×dmg 由全局 CastMixin.apoth_spellPower 统一施加（含“伤害”行经 getUniqueInfo→getDamage 的嵌套调用），
        // 此处只取基础值，避免对直接调用重复乘算。
        return spell.getSpellPower(spellLevel, source);
    }

    /**
     * 拦截 getManaCost：先用 boosted 等级计算，再乘 d.mana()
     */
    @Redirect(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private static int redirectGetManaCost(AbstractSpell spell, int level) {
        int base = spell.getManaCost(level);
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().mana() == 1f) {
            ApotheosisSpells.LOGGER.info("{} formatScrollTooltip getManaCost: spell={}, level={}, base={}, d.mana=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), level, base);
            return base;
        }
        int result = Math.max(0, Math.round(base * ctx.data().mana()));
        ApotheosisSpells.LOGGER.info("{} formatScrollTooltip getManaCost: spell={}, level={}, base={}, d.mana={}, final={}",
                PREFIX, spell.getSpellResource(), level, base, ctx.data().mana(), result);
        return result;
    }

    /**
     * 拦截 getEffectiveCastTime：直接应用 d.cast()
     */
    @Redirect(method = "formatScrollTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private static int redirectGetEffectiveCastTime(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.LivingEntity entity) {
        int base = spell.getEffectiveCastTime(spellLevel, entity);
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().cast() == 1f) {
            ApotheosisSpells.LOGGER.info("{} formatScrollTooltip getEffectiveCastTime: spell={}, level={}, base={}, d.cast=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), spellLevel, base);
            return base;
        }
        int result = Math.max(0, Math.round(base * ctx.data().cast()));
        ApotheosisSpells.LOGGER.info("{} formatScrollTooltip getEffectiveCastTime: spell={}, level={}, base={}, d.cast={}, final={}",
                PREFIX, spell.getSpellResource(), spellLevel, base, ctx.data().cast(), result);
        return result;
    }

    /**
     * 拦截 getLevelComponenet：显示 boosted 等级带 +/-diff。
     * 同时拦 getTitleComponent —— 法术书 hover 的标题(含等级)经 getTitleComponent→getLevelComponenet 嵌套算出，
     * 只拦 formatScrollTooltip 拦不到那次嵌套调用，导致法术书标题显示原始等级(如 10)而非加成后(14)。
     */
    @Redirect(method = {"formatScrollTooltip", "getTitleComponent"}, at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/util/TooltipsUtils;getLevelComponenet(Lio/redspace/ironsspellbooks/api/spells/SpellData;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/network/chat/MutableComponent;"))
    private static net.minecraft.network.chat.MutableComponent redirectGetLevelComponenet(io.redspace.ironsspellbooks.api.spells.SpellData spellData, net.minecraft.world.entity.LivingEntity caster) {
        int stored = spellData.getLevel();
        int level = spellData.getSpell().getLevelFor(stored, caster);
        var ctx = SpellCastHooks.get();
        int diff = 0;
        if (ctx != null && ctx.data() != null && ctx.data().lvl() > 0) {
            level += ctx.data().lvl();
            diff = ctx.data().lvl();
        }
        int diffFromStored = level - stored;
        net.minecraft.network.chat.MutableComponent result;
        if (diffFromStored > 0) {
            result = net.minecraft.network.chat.Component.literal(level + " (+" + diffFromStored + ")");
            ApotheosisSpells.LOGGER.info("{} formatScrollTooltip getLevelComponenet: spell={}, stored={}, afterAffinity={}, d.lvl={}, display={}",
                    PREFIX, spellData.getSpell().getSpellResource(), stored, level - diff, diff, level + " (+" + diffFromStored + ")");
        } else if (diffFromStored < 0) {
            result = net.minecraft.network.chat.Component.literal(level + " (" + diffFromStored + ")");
            ApotheosisSpells.LOGGER.info("{} formatScrollTooltip getLevelComponenet: spell={}, stored={}, afterAffinity={}, d.lvl={}, display={}",
                    PREFIX, spellData.getSpell().getSpellResource(), stored, level - diff, diff, level + " (" + diffFromStored + ")");
        } else {
            result = net.minecraft.network.chat.Component.literal(String.valueOf(level));
            ApotheosisSpells.LOGGER.info("{} formatScrollTooltip getLevelComponenet: spell={}, stored={}, level={}, no d.lvl",
                    PREFIX, spellData.getSpell().getSpellResource(), stored, level);
        }
        return result;
    }

    // ==================== formatActiveSpellTooltip 的重定向 ====================

    /**
     * 拦截 getLevelFor：返回 boosted 等级
     */
    @Redirect(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getLevelFor(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private static int redirectActiveGetLevelFor(AbstractSpell spell, int level, net.minecraft.world.entity.LivingEntity caster) {
        int result = spell.getLevelFor(level, caster);
        var ctx = SpellCastHooks.get();
        int boosted = result;
        if (ctx != null && ctx.data() != null && ctx.data().lvl() > 0) {
            boosted = result + ctx.data().lvl();
            ApotheosisSpells.LOGGER.info("{} formatActiveSpellTooltip getLevelFor: spell={}, originLevel={}, afterAffinity={}, boosted={}, d.lvl={}",
                    PREFIX, spell.getSpellResource(), level, result, boosted, ctx.data().lvl());
        }
        return boosted;
    }

    /**
     * 拦截 getSpellPower：先用 boosted 等级计算，再乘 d.dmg()
     */
    @Redirect(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getSpellPower(ILnet/minecraft/world/entity/Entity;)F"))
    private static float redirectActiveGetSpellPower(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.Entity source) {
        // ×dmg 由全局 CastMixin.apoth_spellPower 统一施加，避免重复。
        return spell.getSpellPower(spellLevel, source);
    }

    /**
     * 拦截 getManaCost：先用 boosted 等级计算，再乘 d.mana()
     */
    @Redirect(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private static int redirectActiveGetManaCost(AbstractSpell spell, int level) {
        int base = spell.getManaCost(level);
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().mana() == 1f) {
            ApotheosisSpells.LOGGER.info("{} formatActiveSpellTooltip getManaCost: spell={}, level={}, base={}, d.mana=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), level, base);
            return base;
        }
        int result = Math.max(0, Math.round(base * ctx.data().mana()));
        ApotheosisSpells.LOGGER.info("{} formatActiveSpellTooltip getManaCost: spell={}, level={}, base={}, d.mana={}, final={}",
                PREFIX, spell.getSpellResource(), level, base, ctx.data().mana(), result);
        return result;
    }

    /**
     * 拦截 getEffectiveCastTime：直接应用 d.cast()
     */
    @Redirect(method = "formatActiveSpellTooltip", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"))
    private static int redirectActiveGetEffectiveCastTime(AbstractSpell spell, int spellLevel, net.minecraft.world.entity.LivingEntity entity) {
        int base = spell.getEffectiveCastTime(spellLevel, entity);
        var ctx = SpellCastHooks.get();
        if (ctx == null || ctx.data() == null || ctx.data().cast() == 1f) {
            ApotheosisSpells.LOGGER.info("{} formatActiveSpellTooltip getEffectiveCastTime: spell={}, level={}, base={}, d.cast=1.0 (no change)",
                    PREFIX, spell.getSpellResource(), spellLevel, base);
            return base;
        }
        int result = Math.max(0, Math.round(base * ctx.data().cast()));
        ApotheosisSpells.LOGGER.info("{} formatActiveSpellTooltip getEffectiveCastTime: spell={}, level={}, base={}, d.cast={}, final={}",
                PREFIX, spell.getSpellResource(), spellLevel, base, ctx.data().cast(), result);
        return result;
    }
}
