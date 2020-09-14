package com.ficat.sample.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;

import java.util.List;

public abstract class CommonExpandableListAdapter<Group, Child> extends BaseExpandableListAdapter {
    private List<Group> mGroupData;
    private List<List<Child>> mChildData;
    private LayoutInflater mInflater;
    private int mGroupResLayout, mChildResLayout;
    private int[] mGroupViewIds, mChildViewIds;

    public CommonExpandableListAdapter(@NonNull Context context, @NonNull List<Group> groupData,
                                       @NonNull List<List<Child>> childData, int groupResLayout, int childResLayout,
                                       int[] groupViewIds, int[] childViewIds) {
        this.mGroupData = groupData;
        this.mChildData = childData;
        if (groupData.size() != childData.size()) {
            throw new IllegalArgumentException("Group data size is not equal Child data size");
        }
        this.mGroupResLayout = groupResLayout;
        this.mChildResLayout = childResLayout;
        this.mGroupViewIds = groupViewIds;
        this.mChildViewIds = childViewIds;
        this.mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getGroupCount() {
        return mGroupData.size();
    }

    @Override
    public int getChildrenCount(int i) {
        return mChildData.get(i).size();
    }

    @Override
    public Object getGroup(int i) {
        return mGroupData.get(i);
    }

    @Override
    public Object getChild(int i, int i1) {
        return mChildData.get(i).get(i1);
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int i, int i1) {
        return i1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup viewGroup) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(mGroupResLayout, viewGroup, false);
            holder = new ViewHolder(convertView, mGroupViewIds);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        bindDataToGroup(viewGroup, holder, mGroupData.get(groupPosition), groupPosition, isExpanded);
        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup viewGroup) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = mInflater.inflate(mChildResLayout, viewGroup, false);
            holder = new ViewHolder(convertView, mChildViewIds);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        bindDataToChild(viewGroup, holder, mChildData.get(groupPosition).get(childPosition), groupPosition, childPosition, isLastChild);
        return convertView;
    }

    public List<Group> getGroupData() {
        return mGroupData;
    }

    public List<List<Child>> getChildData() {
        return mChildData;
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return true;
    }

    public abstract void bindDataToGroup(ViewGroup viewGroup, ViewHolder holder, Group groupData, int groupPosition, boolean isExpanded);

    public abstract void bindDataToChild(ViewGroup viewGroup, ViewHolder holder, Child childData, int groupPosition, int childPosition, boolean isLastChild);

    public static class ViewHolder {
        public SparseArray<View> mViews;

        public ViewHolder(View itemView, int[] resViewIds) {
            mViews = new SparseArray<>();
            for (int viewId : resViewIds) {
                View view = itemView.findViewById(viewId);
                mViews.put(viewId, view);
            }
        }
    }
}
