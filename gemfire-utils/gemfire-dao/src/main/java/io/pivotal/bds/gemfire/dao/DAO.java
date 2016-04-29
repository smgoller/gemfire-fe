package io.pivotal.bds.gemfire.dao;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import com.gemstone.gemfire.cache.execute.RegionFunctionContext;

public interface DAO<K, V> extends Map<K, V>, ConcurrentMap<K, V> {

    V store(K k, V v);

    V delete(K k);

    V retrieve(K k);

    List<V> query(String field, Object value);

    List<V> query(String field, Object value, RegionFunctionContext regionFunctionContext);
}
