package com.subcherry.configuration.properties;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.subcherry.configuration.annotation.NoValueCaching;
import com.subcherry.configuration.annotation.ValueParser;
import com.subcherry.utils.ArrayUtil;

/**
 * @version   $Revision$  $Author$  $Date$
 */
public class PropertiesInvocationHandler extends AbstractPropertiesInvocationHandler {
	
	private static final String STRING_SEPARATOR = "\\s*,\\s*";

	private static final String GET_PREFIX = "get";
	
	private final Map<Method, Object> _resolvedValues = new HashMap<Method, Object>(); 
	private final Properties _config;
	private final String _prefix;

	public PropertiesInvocationHandler(Properties config, String prefix) {
		this._config = config;
		this._prefix = prefix;
	}

	@Override
	protected Object invokeBusinessMethod(Object proxy, Method method, Object[] args) throws Throwable {
		if (_resolvedValues.containsKey(method)) {
			return _resolvedValues.get(method);
		} else {
			checkPureGetter(method);
			String input = getInputValue(method);
			Object resolvedValue = parse(input, method);
			if (method.getAnnotation(NoValueCaching.class) == null) {
				_resolvedValues.put(method, resolvedValue);
			}
			return resolvedValue;
		}
	}
	
	@Override
	protected Properties getConfig() {
		return _config;
	}

	private Object parse(String input, Method method) throws Exception {
		Class<?> returnType = method.getReturnType();
		if (returnType.isArray()) {
			return splitArray(input, returnType);
		}
		ValueParser parserAnnotation = method.getAnnotation(ValueParser.class);
		if (parserAnnotation != null) {
			Class<? extends Parser<?>> value = parserAnnotation.value();
			Parser<?> parserInstance = value.newInstance();
			return parserInstance.parse(input);
		}
		Parser<?> parser = Parsers.getParser(returnType);
		return parser.parse(input);
	}

	private Object splitArray(String input, Class<?> returnType) {
		Class<?> componentType = returnType.getComponentType();
		String[] split;
		if (input == null || input.isEmpty()) {
			split = ArrayUtil.EMPTY_STRING_ARRAY;
		} else {
			split = splitString(input);
		}
		Object result = Array.newInstance(componentType, split.length);
		Parser<?> parser = Parsers.getParser(componentType);
		for (int i = 0 ; i < split.length; i++) {
			Array.set(result, i, parser.parse(split[i]));
		}
		return result;
	}

	private String[] splitString(String input) {
		return input.trim().split(STRING_SEPARATOR);
	}

	private String getInputValue(Method method) {
		String propertyName = getPropertyName(method);
		return getConfig().getProperty(propertyName);
	}

	private String getPropertyName(Method method) {
		String name = method.getName();
		StringBuilder b = new StringBuilder();
		if (_prefix != null) {
			b.append(_prefix).append('.');
		}
		b.append(Character.toLowerCase(name.charAt(GET_PREFIX.length())));
		b.append(name.substring(GET_PREFIX.length() + 1));
		return b.toString();
	}

	private void checkPureGetter(Method method) {
		if (!method.getName().startsWith(GET_PREFIX)) {
			throw noGetter();
		}
		if (method.getParameterTypes().length != 0) {
			throw noGetter();
		}
	}

	private RuntimeException noGetter() {
		throw new UnsupportedOperationException("Only String valued methods of kind 'getXXX()' where XXX is the requested property are supported");
	}

}

