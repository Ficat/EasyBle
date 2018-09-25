package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;
import com.ficat.easyble.gatt.bean.CharacteristicInfo;
import com.ficat.easyble.gatt.bean.ServiceInfo;

import java.util.List;
import java.util.Map;


public interface BleQueryServicesCallback extends BleCallback {
    /**
     * @param servicesInfoMap services info map, the map key represents service info, and the map value
     *                        represents characteristic info list
     * @param device          the device that these services belong to
     */
    void onQueryServices(Map<ServiceInfo, List<CharacteristicInfo>> servicesInfoMap, BleDevice device);
}
