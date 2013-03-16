package com.subcherry.configuration.properties;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class Parsers {

	private static final Map<Class<?>, Parser<?>> _knownParsers = new HashMap<Class<?>, Parser<?>>();
	static {
		_knownParsers.put(Long.class, new LongParser());
		_knownParsers.put(Long.TYPE, new PrimitiveLongParser());
		_knownParsers.put(Integer.class, new IntegerParser());
		_knownParsers.put(Integer.TYPE, new PrimitiveIntegerParser());
		_knownParsers.put(Boolean.class, new BooleanParser());
		_knownParsers.put(Boolean.TYPE, new PrimitiveBooleanParser());
		_knownParsers.put(String.class, new StringParser());
		_knownParsers.put(File.class, new FileParser());
	}

	public static <T> Parser<T> getParser(Class<T> type) {
		@SuppressWarnings("unchecked")
		Parser<T> parser = (Parser<T>) _knownParsers.get(type);
		if (parser == null) {
			throw new UnsupportedOperationException("Can not parse to type '" + type.getName() + "'");
		}
		return parser;

	}

}
