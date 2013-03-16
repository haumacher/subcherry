package com.subcherry.configuration.properties;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * @version $Revision$ $Author$ $Date$
 */
public abstract class AbstractPropertiesInvocationHandler implements InvocationHandler {

	static Method GET_PROPERTY;

	static {
		Method declaredMethod;
		try {
			declaredMethod = PropertyConfiguration.class.getDeclaredMethod("getProperty", String.class);
		} catch (Exception ex) {
			throw new ExceptionInInitializerError(ex);
		}
		GET_PROPERTY = declaredMethod;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (isEquals(method)) {
			return equals(proxy, args[0]);
		}
		if (isHashCode(method)) {
			return hashCode(proxy);
		}
		if (isToString(method)) {
			return getConfig().toString();
		}
		if (GET_PROPERTY == method) {
			return getConfig().getProperty((String) args[0]);
		}
		return invokeBusinessMethod(proxy, method, args);
	}

	private boolean isToString(Method method) {
		return method.getName().equals("toString") && method.getParameterTypes().length == 0
				&& method.getReturnType() == String.class;
	}

	protected abstract Object invokeBusinessMethod(Object proxy, Method method, Object[] args) throws Throwable;

	protected abstract Properties getConfig();

	private Object hashCode(Object proxy) {
		return System.identityHashCode(proxy);
	}

	private boolean isHashCode(Method method) {
		return method.getName().equals("hashCode") && method.getParameterTypes().length == 0
				&& method.getReturnType() == Integer.TYPE;
	}

	private boolean isEquals(Method method) {
		return method.getName().equals("equals") && method.getParameterTypes().length == 1
				&& method.getParameterTypes()[0] == Object.class && method.getReturnType() == Boolean.TYPE;
	}

	private boolean equals(Object proxy, Object other) {
		return proxy == other;
	}

}
