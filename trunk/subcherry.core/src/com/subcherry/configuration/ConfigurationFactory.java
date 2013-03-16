package com.subcherry.configuration;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.Properties;

import com.subcherry.configuration.properties.PropertiesInvocationHandler;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class ConfigurationFactory {

	public static <T> T newConfiguration(Class<T> configurationDescription, Properties config) {
		return newConfiguration(configurationDescription, config, null);
	}

	public static <T> T newConfiguration(Class<T> configurationDescription, String propsFilename) throws IOException {
		return newConfiguration(configurationDescription, propsFilename, null);
	}

	public static <T> T newConfiguration(Class<T> configurationDescription, Properties config, String prefix) {
		PropertiesInvocationHandler h = new PropertiesInvocationHandler(config, prefix);
		Class<?>[] interfaces = new Class<?>[] { configurationDescription };
		ClassLoader classLoader = ClassLoader.getSystemClassLoader();
		return (T) Proxy.newProxyInstance(classLoader, interfaces, h);
	}

	public static <T> T newConfiguration(Class<T> configurationDescription, String propsFilename, String prefix)
			throws IOException {
		Properties properties = readProperties(propsFilename);
		return newConfiguration(configurationDescription, properties, prefix);
	}

	/**
	 * Reads the properties from the file with the given name
	 * 
	 * @param propertiesFileName
	 *        the name of the file to read
	 * @return the read properties
	 * @throws FileNotFoundException
	 *         iff there is no file with the given name
	 * @throws IOException
	 *         iff reading fails
	 */
	public static Properties readProperties(String propertiesFileName) throws IOException {
		Properties properties = new Properties();
		InputStream is = new FileInputStream(propertiesFileName);
		try {
			properties.load(is);
		} finally {
			is.close();
		}
		return properties;
	}

	public static void storeProperties(Properties properties, String propertiesFileName) throws IOException {
		String description = null;
		storeProperties(properties, propertiesFileName, description);
	}

	public static void storeProperties(Properties properties, String propertiesFileName, String description)
			throws IOException {
		FileOutputStream os = new FileOutputStream(propertiesFileName);
		try {
			properties.store(os, description);
		} finally {
			os.close();
		}
	}

}
