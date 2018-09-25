package com.ficat.easyble.gatt.bean;

import java.io.Serializable;

/**
 * Created by pw on 2018/9/19.
 */

public class CharacteristicInfo implements Serializable {
    public String uuid;
    public boolean readable;
    public boolean writeable;
    public boolean notify;
    public boolean indicative;

    public CharacteristicInfo(String uuid, boolean readable, boolean writeable, boolean notify, boolean indicative) {
        this.uuid = uuid;
        this.readable = readable;
        this.writeable = writeable;
        this.notify = notify;
        this.indicative = indicative;
    }

    @Override
    public String toString() {
        return "CharacteristicInfo{" +
                "uuid='" + uuid + '\'' +
                ", readable=" + readable +
                ", writeable=" + writeable +
                ", notify=" + notify +
                ", indicative=" + indicative +
                '}';
    }
}
