package com.github.games647.changeskin.core;

import com.github.games647.changeskin.core.SkinData;

import java.util.UUID;

public class UserPreferences {

    private final UUID uuid;
    private SkinData targetSkin;

    public UserPreferences(UUID uuid, SkinData targetSkin) {
        this.uuid = uuid;
        this.targetSkin = targetSkin;
    }

    public UserPreferences(UUID uuid) {
        this(uuid, null);
    }

    public UUID getUuid() {
        return uuid;
    }

    public synchronized SkinData getTargetSkin() {
        return targetSkin;
    }

    public synchronized void setTargetSkin(SkinData targetSkin) {
        this.targetSkin = targetSkin;
    }
}
