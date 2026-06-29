package com.example.apotheosis_spells.api;

import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;

/**
 * ThreadLocal 状态，用于在 setupResultSlot 和 onTake 之间传递数据。
 *
 * remainingData 按法术槽的<b>稳定 index 字段</b>键存（index -> affix_data），
 * 而非数组下标列表 —— 否则从中间取出法术时，剩余法术会被按错误顺序恢复
 * （表现为"只能从后往前取，否则其他法术变回重铸前"）。
 */
public class SlotOnTakeState {

    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    public static void set(int removedIndex, Map<Integer, CompoundTag> remainingData, InscriptionTableMenu menu) {
        STATE.set(new State(removedIndex, remainingData, menu));
    }

    public static boolean isActive() {
        return STATE.get() != null;
    }

    public static int getRemovedIndex() {
        State s = STATE.get();
        return s != null ? s.removedIndex : -1;
    }

    public static Map<Integer, CompoundTag> getRemainingData() {
        State s = STATE.get();
        return s != null ? s.remainingData : null;
    }

    public static InscriptionTableMenu getMenu() {
        State s = STATE.get();
        return s != null ? s.menu : null;
    }

    public static void clear() {
        STATE.remove();
    }

    private record State(int removedIndex, Map<Integer, CompoundTag> remainingData, InscriptionTableMenu menu) {}
}
