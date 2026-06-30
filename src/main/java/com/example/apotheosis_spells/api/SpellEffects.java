package com.example.apotheosis_spells.api;

/**
 * 「事件类特效」聚合层 —— 与 {@link ReforgeCache.Data}（倍率类：伤害/法力/冷却/吟唱/等级/范围/时长/学派）
 * 平行的另一套词条数据，承载需要在 Iron's <b>事件</b>（SpellDamageEvent / SpellOnCastEvent /
 * ModifySpellLevelEvent / SpellCooldownAddedEvent）里结算的特效：吸血、暴击、斩杀、超载、回响、免冷却、
 * 施法护盾、疾步等。由 {@code handler.SpellEffectHandler} 在事件触发时从施法者手里的卷轴/书<b>实时解析</b>
 * （不走 ThreadLocal，故投射物飞出后命中那一刻也能正确结算）。
 *
 * <p>设计上不缓存（每次事件实时 {@link ReforgeCache#computeEffects} 聚合），避免侵入现有 Data 的缓存/读写逻辑。
 * 字段全为可累加数值，{@link #merge} 把同一物品上多个特效词条相加（几率类 clamp 到 [0,1]）。
 */
public record SpellEffects(
        float leech,        // 法术吸血：伤害的该比例回复生命
        float manaLeech,    // 法力虹吸：伤害的该比例回复法力
        float critChance,   // 暴击几率 [0,1]
        float execute,      // 斩杀：对低血目标的额外伤害比例（×(1+execute)）
        float overcharge,   // 超载几率 [0,1]：本次施法等级 +OVERCHARGE_LEVELS
        float echo,         // 回响几率 [0,1]：免费再触发一次
        int recast,         // 连发增幅：recast 类法术 +N 段
        float cdSkip,       // 免冷却几率 [0,1]
        float shield,       // 施法护盾：施法获得的吸收护盾点数
        int haste) {        // 疾步：施法后迅捷等级（0=无）

    public static final SpellEffects NONE = new SpellEffects(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public boolean isEmpty() {
        return leech == 0 && manaLeech == 0 && critChance == 0 && execute == 0
                && overcharge == 0 && echo == 0 && recast == 0 && cdSkip == 0
                && shield == 0 && haste == 0;
    }

    /** 合并两个特效（同物品多词条）。数值相加，几率 clamp[0,1]，haste 取较大。 */
    public SpellEffects merge(SpellEffects o) {
        if (o == null || o.isEmpty()) return this;
        if (this.isEmpty()) return o;
        return new SpellEffects(
                leech + o.leech,
                manaLeech + o.manaLeech,
                clamp01(critChance + o.critChance),
                execute + o.execute,
                clamp01(overcharge + o.overcharge),
                clamp01(echo + o.echo),
                recast + o.recast,
                clamp01(cdSkip + o.cdSkip),
                shield + o.shield,
                Math.max(haste, o.haste));
    }

    private static float clamp01(float f) { return f < 0 ? 0 : (f > 1 ? 1 : f); }

    // —— 便捷构造（每个特效词条只设自己那一项）——
    public static SpellEffects ofLeech(float v)      { return new SpellEffects(v, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellEffects ofManaLeech(float v)  { return new SpellEffects(0, v, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellEffects ofCrit(float v)       { return new SpellEffects(0, 0, v, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellEffects ofExecute(float v)    { return new SpellEffects(0, 0, 0, v, 0, 0, 0, 0, 0, 0); }
    public static SpellEffects ofOvercharge(float v) { return new SpellEffects(0, 0, 0, 0, v, 0, 0, 0, 0, 0); }
    public static SpellEffects ofEcho(float v)       { return new SpellEffects(0, 0, 0, 0, 0, v, 0, 0, 0, 0); }
    public static SpellEffects ofRecast(int v)       { return new SpellEffects(0, 0, 0, 0, 0, 0, v, 0, 0, 0); }
    public static SpellEffects ofCdSkip(float v)     { return new SpellEffects(0, 0, 0, 0, 0, 0, 0, v, 0, 0); }
    public static SpellEffects ofShield(float v)     { return new SpellEffects(0, 0, 0, 0, 0, 0, 0, 0, v, 0); }
    public static SpellEffects ofHaste(int v)        { return new SpellEffects(0, 0, 0, 0, 0, 0, 0, 0, 0, v); }
}
