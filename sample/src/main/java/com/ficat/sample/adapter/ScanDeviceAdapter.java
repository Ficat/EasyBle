package com.ficat.sample.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.SparseArray;
import android.widget.TextView;

import com.ficat.easyble.BleDevice;
import com.ficat.sample.R;

import java.util.List;


public class ScanDeviceAdapter extends CommonRecyclerViewAdapter<BleDevice> {

    public ScanDeviceAdapter(@NonNull Context context, @NonNull List<BleDevice> dataList, @NonNull SparseArray<int[]> resLayoutAndViewIds) {
        super(context, dataList, resLayoutAndViewIds);
    }

    @Override
    public int getItemResLayoutType(int position) {
        return R.layout.item_rv_scan_devices;
    }

    @Override
    public void bindDataToItem(CommonRecyclerViewAdapter.MyViewHolder holder, BleDevice data, int position) {
        TextView tvName = (TextView) holder.mViews.get(R.id.tv_name);
        TextView tvAddress = (TextView) holder.mViews.get(R.id.tv_address);
        tvName.setText(data.name);
        tvAddress.setText(data.address);
    }
}
