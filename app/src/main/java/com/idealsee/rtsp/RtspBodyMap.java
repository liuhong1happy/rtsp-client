package com.idealsee.rtsp;

import java.util.ArrayList;

/**
 * RtspBodyMap
 */
public class RtspBodyMap<K,V> {
    private ArrayList<K> _keys;
    private ArrayList<V> _values;
    public RtspBodyMap() {
        _keys = new ArrayList<K>();
        _values = new ArrayList<V>();
    }
    public void put(K key,V value) {
        _keys.add(key);
        _values.add(value);
    }

    public void putAll(RtspBodyMap<K,V> map) {
        _keys.addAll(map.keys());
        _values.addAll(map.values());
    }

    public ArrayList<V> values() {
        return _values;
    }

    public ArrayList<K> keys() {
        return _keys;
    }

    public Integer length() {
        return _keys.size();
    }

    public V getValue(Integer i) {
        return _values.get(i);
    }

    public K getKey(Integer i) {
        return _keys.get(i);
    }
}