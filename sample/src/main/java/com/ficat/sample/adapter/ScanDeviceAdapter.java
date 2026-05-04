package com.ficat.sample.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.SparseArray;
import android.widget.TextView;

import com.ficat.easyble.BleDevice;
import com.ficat.sample.R;
import com.ficat.sample.data.ExtraInfo;

import java.util.List;


public class ScanDeviceAdapter extends CommonRecyclerViewAdapter<BleDevice> {
    private OnClickToOperationPageButtonListener mClickToOperationPageButtonListener;

    public interface OnClickToOperationPageButtonListener {
        void onClickToOperationPage(int position);
    }

    public ScanDeviceAdapter(@NonNull Context context, @NonNull List<BleDevice> dataList, @NonNull SparseArray<int[]> resLayoutAndViewIds) {
        super(context, dataList, resLayoutAndViewIds);
    }

    public void setOnClickToOperationPageButtonListener(OnClickToOperationPageButtonListener mClickToOperationPageButtonListener) {
        this.mClickToOperationPageButtonListener = mClickToOperationPageButtonListener;
    }

    @Override
    public int getItemResLayoutType(int position) {
        return R.layout.item_rv_scan_devices;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void bindDataToItem(CommonRecyclerViewAdapter.MyViewHolder holder, BleDevice data, int position) {
        TextView tvName = (TextView) holder.mViews.get(R.id.tv_name);
        TextView tvAddress = (TextView) holder.mViews.get(R.id.tv_address);
        TextView tvRssi = (TextView) holder.mViews.get(R.id.tv_rssi);
        TextView tvToOperationPage = (TextView) holder.mViews.get(R.id.tv_connection_state);
        String name = data.getName();
        tvName.setText(TextUtils.isEmpty(name) ? "null" : name);
        tvAddress.setText(data.getAddress());
        Parcelable extra = data.getParcelableExtra();
        if (extra instanceof ExtraInfo) {
            int rssi = ((ExtraInfo) extra).getRssi();
            tvRssi.setText(String.format("%d%s", rssi, mContext.getString(R.string.device_rssi_suffix)));
        }
        if (this.mClickToOperationPageButtonListener != null){
            tvToOperationPage.setOnClickListener(v -> mClickToOperationPageButtonListener.onClickToOperationPage(position));
        }
    }

}