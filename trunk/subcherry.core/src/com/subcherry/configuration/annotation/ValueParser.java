package com.subcherry.configuration.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.subcherry.configuration.properties.Parser;

/**
 * @version   $Revision$  $Author$  $Date$
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ValueParser {
	
	Class<? extends Parser<?>> value();

}

