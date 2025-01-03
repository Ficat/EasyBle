package com.ficat.easyble.gatt.data;

import java.io.Serializable;
import java.util.UUID;


public class ServiceInfo implements Serializable {
    private final UUID uuid;

    public ServiceInfo(UUID uuid) {
        this.uuid = new UUID(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    public UUID getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
                "uuid=" + uuid +
                '}';
    }
}
