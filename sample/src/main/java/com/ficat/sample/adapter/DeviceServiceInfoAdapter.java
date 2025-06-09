package com.ficat.sample.adapter;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ficat.easyble.utils.BluetoothGattUtils;
import com.ficat.sample.R;

import java.util.List;

public class DeviceServiceInfoAdapter extends CommonExpandableListAdapter<BluetoothGattService, BluetoothGattCharacteristic> {
    public DeviceServiceInfoAdapter(@NonNull Context context, @NonNull List<BluetoothGattService> groupData,
                                    @NonNull List<List<BluetoothGattCharacteristic>> childData, int groupResLayout,
                                    int childResLayout, int[] groupViewIds, int[] childViewIds) {
        super(context, groupData, childData, groupResLayout, childResLayout, groupViewIds, childViewIds);
    }

    @Override
    public void bindDataToGroup(ViewGroup viewGroup, ViewHolder holder, BluetoothGattService groupData, int groupPosition, boolean isExpanded) {
        TextView tvServiceUuid = (TextView) holder.mViews.get(R.id.tv_service_uuid);
        tvServiceUuid.setText(groupData.getUuid().toString());
    }

    @Override
    public void bindDataToChild(ViewGroup viewGroup, ViewHolder holder, BluetoothGattCharacteristic childData, int groupPosition, int childPosition, boolean isLastChild) {
        TextView tvUuid = (TextView) holder.mViews.get(R.id.tv_characteristic_uuid);
        TextView tvAttribution = (TextView) holder.mViews.get(R.id.tv_characteristic_attribution);
        tvUuid.setText(childData.getUuid().toString());
        String attri = "";
        if (BluetoothGattUtils.isCharacteristicNotifiable(childData)) {
            attri += "Notify  ";
        }
        if (BluetoothGattUtils.isCharacteristicIndicative(childData)) {
            attri += "Indicate  ";
        }
        if (BluetoothGattUtils.isCharacteristicReadable(childData)) {
            attri += "Read ";
        }
        if (BluetoothGattUtils.isCharacteristicWritable(childData)) {
            attri += "Write ";
        }
        tvAttribution.setText(attri);
    }
}
