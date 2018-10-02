package com.ficat.sample.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.SparseArray;
import android.widget.TextView;

import com.ficat.easyble.BleDevice;
import com.ficat.sample.R;
import com.ficat.sample.common.CommonRecyclerViewAdapter;

import java.util.List;


public class ScanDeviceAdapter extends CommonRecyclerViewAdapter<BleDevice> {

    public ScanDeviceAdapter(@NonNull Context context, @NonNull List<BleDevice> dataList, @NonNull SparseArray<int[]> resLayoutAndViewIds) {
        super(context, dataList, resLayoutAndViewIds);
    }

    @Override
    public int getItemReslayoutType(int position) {
        return R.layout.item_rv_scan_devices;
    }

    @Override
    public void bindDataToItem(MyViewHolder holder, BleDevice data, int position) {
        TextView name = (TextView) holder.mViews.get(R.id.tv_name);
        TextView address = (TextView) holder.mViews.get(R.id.tv_address);
        TextView connectionState = (TextView) holder.mViews.get(R.id.tv_connection_state);
        name.setText(data.name);
        address.setText(data.address);
        if (data.connected) {
            connectionState.setText("connected");
        } else {
            connectionState.setText("disconnected");
        }
    }
}
