package com.ficat.easyble.gatt.data;

import java.io.Serializable;
import java.util.UUID;

/**
 * Created by pw on 2018/9/19.
 */

public class CharacteristicInfo implements Serializable {
    private final UUID uuid;
    private final boolean readable;
    private final boolean writable;
    private final boolean notifiable;
    private final boolean indicative;

    public CharacteristicInfo(UUID uuid, boolean readable, boolean writable, boolean notifiable, boolean indicative) {
        this.uuid = new UUID(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
        this.readable = readable;
        this.writable = writable;
        this.notifiable = notifiable;
        this.indicative = indicative;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isReadable() {
        return readable;
    }

    public boolean isWritable() {
        return writable;
    }

    public boolean isNotifiable() {
        return notifiable;
    }

    public boolean isIndicative() {
        return indicative;
    }

    @Override
    public String toString() {
        return "CharacteristicInfo{" +
                "uuid=" + uuid +
                ", readable=" + readable +
                ", writable=" + writable +
                ", notifiable=" + notifiable +
                ", indicative=" + indicative +
                '}';
    }
}
