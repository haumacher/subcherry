/*
 * TimeCollect records time you spent on your development work.
 * Copyright (C) 2014 Bernhard Haumacher and others
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.subcherry.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectionUtil {

	public static <K, V> List<V> mkList(Map<K, List<V>> multiMap, K key) {
		List<V> list = multiMap.get(key);
		if (list == null) {
			list = new ArrayList<>();
			multiMap.put(key, list);
		}
		return list;
	}

	public static <K, V> Set<V> mkSet(Map<K, Set<V>> multiMap, K key) {
		Set<V> list = multiMap.get(key);
		if (list == null) {
			list = new HashSet<>();
			multiMap.put(key, list);
		}
		return list;
	}

	public static <K1, K2, V> Map<K2, V> mkMap(Map<K1, Map<K2, V>> multiMap, K1 key) {
		Map<K2, V> map = multiMap.get(key);
		if (map == null) {
			map = new HashMap<>();
			multiMap.put(key, map);
		}
		return map;
	}

	public static <K extends Comparable<? super K>> List<K> keysSorted(Map<K, ?> map) {
		return CollectionUtil.sorted(map.keySet());
	}

	public static <K> List<K> keysSorted(Map<K, ?> map, Comparator<? super K> order) {
		return CollectionUtil.sorted(map.keySet(), order);
	}

	public static <K extends Comparable<? super K>> List<K> sorted(Collection<K> collection) {
		List<K> requiredTicketIds = new ArrayList<>(collection);
		Collections.sort(requiredTicketIds);
		return requiredTicketIds;
	}

	public static <K> List<K> sorted(Collection<K> collection, Comparator<? super K> order) {
		List<K> requiredTicketIds = new ArrayList<>(collection);
		Collections.sort(requiredTicketIds, order);
		return requiredTicketIds;
	}

}
