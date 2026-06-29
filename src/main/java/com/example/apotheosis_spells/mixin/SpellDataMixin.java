package com.example.apotheosis_spells.mixin;

import com.example.apotheosis_spells.handler.SpellCastHooks;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(value = SpellData.class, remap = false)
public class SpellDataMixin {

    private static final Field SPELL_LEVEL_FIELD;

    static {
        Field f;
        try {
            f = SpellData.class.getDeclaredField("spellLevel");
            f.setAccessible(true);
        } catch (Exception e) {
            f = null;
        }
        SPELL_LEVEL_FIELD = f;
    }

    private static int getRawLevel(SpellData data) {
        if (SPELL_LEVEL_FIELD == null || data == null) return -1;
        try {
            return SPELL_LEVEL_FIELD.getInt(data);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * UI 路径（tooltip、spell wheel、铭刻界面）下，entry mixin 会把当前 stack/slot 的 ctx 写入 ThreadLocal。
     * 当 spellData.getLevel() 被调用时（UI 渲染 spell level），
     * 如果 self 的 spell+level 跟 ctx.spellData 匹配（同一 scroll/slot），则加上 ctx.data.lvl() 加成。
     *
     * 实际施法路径下 ctx 不会被 entry mixin 设置（attemptInitiateCast 设的 ctx 已在 captureReturn 清掉），
     * 所以这里 spellData.getLevel() 返回原字段值（5）。CastMixin.modifyCastSpellLevel 单独 boost spellLevel 参数。
     *
     * 用反射读 spellLevel 字段避免 self.getLevel() 递归触发 mixin。
     */
    @Inject(method = "getLevel", at = @At("RETURN"), cancellable = true)
    private void onGetLevel(CallbackInfoReturnable<Integer> cir) {
        // 禁用：等级 boost 由 SpellWheelMixin.redirectGetLevelFor 处理
        // var ctx = SpellCastHooks.get();
        // if (ctx == null) return;
        // if (ctx.data() == null || ctx.data().lvl() == 0) return;
        // if (ctx.spellData() == null) return;
        //
        // int selfLevel = cir.getReturnValue();
        // SpellData self = (SpellData) (Object) this;
        // if (ctx.spellData().getSpell() != self.getSpell()) return;
        // int ctxLevel = getRawLevel(ctx.spellData());
        // if (ctxLevel < 0 || ctxLevel != selfLevel) return;
        //
        // cir.setReturnValue(selfLevel + ctx.data().lvl());
    }
}
