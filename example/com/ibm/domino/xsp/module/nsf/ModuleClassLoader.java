package com.ibm.domino.xsp.module.nsf;

public class ModuleClassLoader extends ClassLoader{
	public Class findClass(String name){
		System.out.println("Original findClass -Searching for class " + name);
		return ModuleClassLoader.class;
	}
}
