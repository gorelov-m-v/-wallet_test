package com.uplatform.wallet_tests.api.db.entity.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameSessionMode {

    REAL((short) 1, "GAME_MODE_REAL"),
    DEMO((short) 2, "GAME_MODE_DEMO");

    private final short id;
    private final String name;

    public static GameSessionMode fromId(short id) {
        for (GameSessionMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown game session mode ID: " + id);
    }

    public static GameSessionMode fromIdOrNull(Short id) {
        if (id == null) {
            return null;
        }
        for (GameSessionMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return null;
    }
}