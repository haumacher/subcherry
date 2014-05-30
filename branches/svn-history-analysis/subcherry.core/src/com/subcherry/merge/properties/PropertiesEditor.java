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
package com.subcherry.merge.properties;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;

/**
 * Editor for {@link Properties} files in non-XML format.
 * 
 * @see Properties#load(InputStream)
 */
public class PropertiesEditor {

	/**
	 * Representation of a single property assignment in a {@link Properties} file.
	 */
	public static class Property implements Map.Entry<String, String> {
		private final String _key;

		private String _value;

		private Property last;

		private Property next;

		private String _source;

		/**
		 * Creates a {@link Property}.
		 * 
		 * @param key
		 *        See {@link #getKey()}.
		 * @param value
		 *        See {@link #getValue()}.
		 * @param source
		 *        See {@link #getSource()}.
		 */
		public Property(String key, String value, String source) {
			_key = key;
			_value = value;
			_source = source;
		}

		/**
		 * The property key (part before the assignment operator).
		 * 
		 * @return The property key, or <code>null</code> if this is represents a comment or empty
		 *         line in the file.
		 */
		@Override
		public String getKey() {
			return _key;
		}

		/**
		 * The property value (part after the assignment operator).
		 */
		@Override
		public String getValue() {
			return _value;
		}

		@Override
		public String setValue(String value) {
			String oldValue = _value;
			if (_value == null || !_value.equals(value)) {
				_value = value;
				_source = null;
			}
			return oldValue;
		}

		/**
		 * The source code line(s) that represent the property assignment (or comment).
		 */
		public String getSource() {
			return _source;
		}

		public Property getNext() {
			return next;
		}

		void setNext(Property next) {
			this.next = next;
		}

		public Property getPrev() {
			return last;
		}

		void setPrev(Property last) {
			this.last = last;
		}

		public Property copy() {
			return new Property(getKey(), getValue(), getSource());
		}

		public Property copySource() {
			return new Property(null, null, getSource());
		}

	}

	Property _first;

	private Property _last;

	private final Map<String, Property> _values = new LinkedHashMap<String, Property>();

	private Map<String, String> _map = new AbstractMap<String, String>() {
		private Set<Entry<String, String>> _entrySet = new AbstractSet<Map.Entry<String, String>>() {

			@Override
			public Iterator<Entry<String, String>> iterator() {
				return Collections.<Entry<String, String>> unmodifiableCollection(_values.values()).iterator();
			}

			@Override
			public int size() {
				return _values.size();
			}

		};

		@Override
		public Set<Entry<String, String>> entrySet() {
			return _entrySet;
		}

		@Override
		public Set<String> keySet() {
			return _values.keySet();
		}

		@Override
		public String get(Object key) {
			Property property = _values.get(key);
			if (property == null) {
				return null;
			}
			return property.getValue();
		}

		@Override
		public String put(String key, String value) {
			Property before = addProperty(new Property(key, value, null));
			if (before == null) {
				return null;
			} else {
				return before.getValue();
			}
		}
	};

	public Map<String, String> asMap() {
		return _map;
	}

	public Property getFirst() {
		return _first;
	}

	public Property getLast() {
		return _last;
	}

	public Iterable<Property> getProperties() {
		return new Iterable<Property>() {
			@Override
			public Iterator<Property> iterator() {
				return new Iterator<Property>() {
					Property _current = _first;

					@Override
					public boolean hasNext() {
						return _current != null;
					}

					@Override
					public Property next() {
						if (!hasNext()) {
							throw new NoSuchElementException();
						}

						Property result = _current;
						_current = _current.getNext();
						return result;
					}

					@Override
					public void remove() {
						Property removed = _current;
						_current = _current.getNext();

						removeProperty(removed);
					}
				};
			}
		};
	}

	public void removeProperty(Property property) {
		String key = property.getKey();
		if (key != null) {
			Property removed = _values.remove(key);
			assert removed == property;
		}

		unlink(property);

		// Remove preceding space, if the following entry is also a space.
		Property next = property.getNext();
		if (next != null && next.getKey() == null) {
			for (Property prev = property.getPrev(); prev != null && prev.getKey() == null; prev = prev.getPrev()) {
				unlink(prev);
			}
		}
	}

	private void unlink(Property property) {
		Property prev = property.getPrev();
		Property next = property.getNext();
		if (prev == null) {
			_first = next;
		} else {
			prev.setNext(next);
		}
		if (next == null) {
			_last = prev;
		} else {
			next.setPrev(prev);
		}
	}

	public Property addProperty(Property property) {
		return addProperty(property, null);
	}

	public Property addProperty(Property property, Property before) {
		String key = property.getKey();

		// Might be a comment no contributing to the values.
		Property oldProperty;
		if (key != null) {
			oldProperty = _values.put(key, property);
			if (oldProperty != null) {
				unlink(oldProperty);
			}
		} else {
			oldProperty = null;
		}

		Property insertBefore = oldProperty == null ? before : oldProperty.getNext();
		link(property, insertBefore);

		return oldProperty;
	}

	private void link(Property property, Property before) {
		if (before == null) {
			// Append.
			if (_last == null) {
				// Was empty.
				_first = _last = property;
			} else {
				_last.setNext(property);
				property.setPrev(_last);

				_last = property;
			}
		} else {
			Property prev = before.getPrev();
			if (prev == null) {
				_first = property;
			} else {
				prev.setNext(property);
			}
			property.setPrev(prev);

			before.setPrev(property);
			property.setNext(before);
		}
	}

	public Property getProperty(String key) {
		return _values.get(key);
	}

	public PropertiesEditor copy() {
		PropertiesEditor result = new PropertiesEditor();
		for (Property property : getProperties()) {
			result.addProperty(property.copy());
		}
		return result;
	}

	/**
	 * @see Properties#load(InputStream)
	 */
	public void load(InputStream in) throws IOException {
		PropertiesIO.load(this, in);
	}

	/**
	 * @see Properties#store(OutputStream, String)
	 */
	public void store(OutputStream out) throws IOException {
		PropertiesIO.store(this, out);
	}

}
