package com.example.apotheosis_spells.api;

import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * ThreadLocal 状态，用于在 setupResultSlot 和 onTake 之间传递数据
 */
public class SlotOnTakeState {

    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    public static void set(int removedIndex, List<CompoundTag> remainingData, InscriptionTableMenu menu) {
        STATE.set(new State(removedIndex, remainingData, menu));
    }

    public static boolean isActive() {
        return STATE.get() != null;
    }

    public static int getRemovedIndex() {
        State s = STATE.get();
        return s != null ? s.removedIndex : -1;
    }

    public static List<CompoundTag> getRemainingData() {
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

    private record State(int removedIndex, List<CompoundTag> remainingData, InscriptionTableMenu menu) {}
}