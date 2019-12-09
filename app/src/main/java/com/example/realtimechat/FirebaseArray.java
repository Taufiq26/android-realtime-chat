package com.example.realtimechat;

import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;

import java.util.ArrayList;

public class FirebaseArray implements ChildEventListener {

    public interface OnChangedListener {
        enum EventType { Added, Changed, Removed, Moved }
        void onChanged(EventType type, int index, int oldIndex);
    }

    private Query mQuery;
    private OnChangedListener mListener;
    private ArrayList<DataSnapshot> mSnapshots;

    public FirebaseArray(Query ref) {
        mQuery = ref;
        mSnapshots = new ArrayList<DataSnapshot>();
        mQuery.addChildEventListener(this);
    }

    public void cleanup() {
        mQuery.removeEventListener(this);
    }

    public int getCount() {
        return mSnapshots.size();

    }
    public DataSnapshot getItem(int index) {
        return mSnapshots.get(index);
    }

    private int getIndexForKey(String key) {
        int index = 0;
        for (DataSnapshot snapshot : mSnapshots) {
            if (snapshot.getKey().equals(key)) {
                return index;
            } else {
                index++;
            }
        }
        throw new IllegalArgumentException("Key not found");
    }

    // ChildEventListener methods
    @Override
    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
        int index = 0;
        if (s != null) {
            index = getIndexForKey(s) + 1;
        }
        mSnapshots.add(index, dataSnapshot);
        notifyChangedListeners(OnChangedListener.EventType.Added, index);
    }

    @Override
    public void onChildChanged(DataSnapshot dataSnapshot, String s) {
        int index = getIndexForKey(dataSnapshot.getKey());
        mSnapshots.set(index, dataSnapshot);
        notifyChangedListeners(OnChangedListener.EventType.Changed, index);
    }

    @Override
    public void onChildRemoved(DataSnapshot dataSnapshot) {
        int index = getIndexForKey(dataSnapshot.getKey());
        mSnapshots.remove(index);
        notifyChangedListeners(OnChangedListener.EventType.Removed, index);
    }

    @Override
    public void onChildMoved(DataSnapshot dataSnapshot, String s) {
        int oldIndex = getIndexForKey(dataSnapshot.getKey());
        mSnapshots.remove(oldIndex);
        int newIndex = s == null ? 0 : (getIndexForKey(s) + 1);
        mSnapshots.add(newIndex, dataSnapshot);
        notifyChangedListeners(OnChangedListener.EventType.Moved, newIndex, oldIndex);
    }

    @Override
    public void onCancelled(FirebaseError firebaseError) {

    }
    // End of ChildEventListener methods

    public void setOnChangedListener(OnChangedListener listener) {
        mListener = listener;
    }
    protected void notifyChangedListeners(OnChangedListener.EventType type, int index) {
        notifyChangedListeners(type, index, -1);
    }
    protected void notifyChangedListeners(OnChangedListener.EventType type, int index, int oldIndex) {
        if (mListener != null) {
            mListener.onChanged(type, index, oldIndex);
        }
    }
}
