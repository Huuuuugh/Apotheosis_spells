package com.example.apotheosis_spells.api;

/**
 * 「事件类特效」聚合层 —— 与 {@link ReforgeCache.Data}（倍率类）平行，承载需要在 Iron's <b>事件</b>里结算的特效。
 * 由 {@code handler.SpellEffectHandler} 在事件触发时从施法者手里的卷轴/书<b>实时解析</b>。不缓存。
 *
 * <p>{@code signature}/{@code signatureValue}：学派签名词条（只在对应学派卷轴上 roll），signature=学派 id(1..9)，
 * signatureValue=该词条数值(含义随学派，见 SpellEffectHandler 的 switch)；handler 据此施加学派专属效果
 * （火焰点燃 / 末影闪现 / 邪术梦魇 等）。
 */
public record SpellEffects(
        float leech, float manaLeech, float critChance, float execute,
        float overcharge, float echo, int recast, float cdSkip,
        float shield, int haste,
        int signature, float signatureValue) {

    public static final SpellEffects NONE = new SpellEffects(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public boolean isEmpty() {
        return leech == 0 && manaLeech == 0 && critChance == 0 && execute == 0
                && overcharge == 0 && echo == 0 && recast == 0 && cdSkip == 0
                && shield == 0 && haste == 0 && signature == 0;
    }

    /** 合并（同物品多词条）。数值相加，几率 clamp[0,1]，haste 取较大；signature 取非零者（学派限定 → 至多一个）。 */
    public SpellEffects merge(SpellEffects o) {
        if (o == null || o.isEmpty()) return this;
        if (this.isEmpty()) return o;
        int sig = signature != 0 ? signature : o.signature;
        float sigv = signature != 0 ? signatureValue : o.signatureValue;
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
                Math.max(haste, o.haste),
                sig, sigv);
    }

    private static float clamp01(float f) { return f < 0 ? 0 : (f > 1 ? 1 : f); }

    // —— 便捷构造（每个词条只设自己那一项）——
    public static SpellEffects ofLeech(float v)      { return new SpellEffects(v, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellEffects ofManaLeech(float v)  { return new SpellEffects(0, v, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellEffects ofCrit(float v)       { return new SpellEffects(0, 0, v, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellEffects ofExecute(float v)    { return new SpellEffects(0, 0, 0, v, 0, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellEffects ofOvercharge(float v) { return new SpellEffects(0, 0, 0, 0, v, 0, 0, 0, 0, 0, 0, 0); }
    public static SpellEffects ofEcho(float v)       { return new SpellEffects(0, 0, 0, 0, 0, v, 0, 0, 0, 0, 0, 0); }
    public static SpellEffects ofRecast(int v)       { return new SpellEffects(0, 0, 0, 0, 0, 0, v, 0, 0, 0, 0, 0); }
    public static SpellEffects ofCdSkip(float v)     { return new SpellEffects(0, 0, 0, 0, 0, 0, 0, v, 0, 0, 0, 0); }
    public static SpellEffects ofShield(float v)     { return new SpellEffects(0, 0, 0, 0, 0, 0, 0, 0, v, 0, 0, 0); }
    public static SpellEffects ofHaste(int v)        { return new SpellEffects(0, 0, 0, 0, 0, 0, 0, 0, 0, v, 0, 0); }
    public static SpellEffects ofSignature(int school, float value) { return new SpellEffects(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, school, value); }
}
