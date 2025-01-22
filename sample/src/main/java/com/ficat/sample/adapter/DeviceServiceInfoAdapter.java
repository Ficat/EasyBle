package com.ficat.sample.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ficat.easyble.gatt.data.CharacteristicInfo;
import com.ficat.easyble.gatt.data.ServiceInfo;
import com.ficat.sample.R;

import java.util.List;

public class DeviceServiceInfoAdapter extends CommonExpandableListAdapter<ServiceInfo,CharacteristicInfo> {
    public DeviceServiceInfoAdapter(@NonNull Context context, @NonNull List<ServiceInfo> groupData,
                                    @NonNull List<List<CharacteristicInfo>> childData, int groupResLayout,
                                    int childResLayout, int[] groupViewIds, int[] childViewIds) {
        super(context, groupData, childData, groupResLayout, childResLayout, groupViewIds, childViewIds);
    }

    @Override
    public void bindDataToGroup(ViewGroup viewGroup, ViewHolder holder, ServiceInfo groupData, int groupPosition, boolean isExpanded) {
        TextView tvServiceUuid= (TextView) holder.mViews.get(R.id.tv_service_uuid);
        tvServiceUuid.setText(groupData.getUuid().toString());
    }

    @Override
    public void bindDataToChild(ViewGroup viewGroup, ViewHolder holder, CharacteristicInfo childData, int groupPosition, int childPosition, boolean isLastChild) {
        TextView tvUuid = (TextView) holder.mViews.get(R.id.tv_characteristic_uuid);
        TextView tvAttribution = (TextView) holder.mViews.get(R.id.tv_characteristic_attribution);
        tvUuid.setText(childData.getUuid().toString());
        String attri="";
        if (childData.isNotifiable()){
            attri += "Notify  ";
        }
        if (childData.isIndicative()){
            attri += "Indicate  ";
        }
        if (childData.isReadable()){
            attri += "Read ";
        }
        if (childData.isWritable()){
            attri += "Write ";
        }
        tvAttribution.setText(attri);
    }
}
