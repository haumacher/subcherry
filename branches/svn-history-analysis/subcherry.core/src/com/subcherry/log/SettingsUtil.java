package com.subcherry.log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class SettingsUtil {

	public static Properties loadConfig(File settingsFile) throws IOException {
		Properties loadedProperties = new Properties();
		if (settingsFile.exists()) {
			FileInputStream settingsIn = new FileInputStream(settingsFile);
			loadedProperties.load(settingsIn);
			settingsIn.close();
		}
		return loadedProperties;
	}

	public static void saveConfig(File settingsFile, Properties properties) throws IOException {
		FileOutputStream settingsOut = new FileOutputStream(settingsFile);
		properties.store(settingsOut, "Ticket check settings");
		settingsOut.close();
	}

}
