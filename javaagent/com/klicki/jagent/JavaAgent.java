package com.klicki.jagent;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class JavaAgent {
	public static void premain(String args, Instrumentation instrumentation){
		ClassHandler tr = new ClassHandler();
		instrumentation.addTransformer(tr);
	
	}

	public static class ClassHandler implements ClassFileTransformer{
		@Override
		public byte[] transform(ClassLoader loader, String className, 
					Class<?> classRedefined, ProtectionDomain pd, 
					byte[] buff) throws IllegalClassFormatException {
			System.out.println(className);
			return buff;
		}
	}
}

