package com.ibm.domino.xsp.module.nsf;

public class ModuleClassLoader extends ClassLoader{
	protected Class defineClass2(String name, byte[] b, int off, int len){
		System.out.println("Defining new class2: " + name);
		return ModuleClassLoader.class;
	}
	
	public Class findClass(String name){
		System.out.println("Original findClass -Searching for class " + name);
		return ModuleClassLoader.class;
	}
}
