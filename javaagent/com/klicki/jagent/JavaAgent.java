package com.klicki.jagent;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.io.File;
import java.nio.file.*;

public class JavaAgent {
	static  long starttime = System.currentTimeMillis();

	public static void premain(String args, Instrumentation instrumentation){
		System.out.println("Adding timing class");
		ClassHandler tr = new ClassHandler();
		instrumentation.addTransformer(tr);
	}

	public static class ClassHandler implements ClassFileTransformer{
		private static final String base = "C:/Users/Dysant Workstation/odk_workspace/FPA/WebContent/WEB-INF/classes"; 
		@Override
		public byte[] transform(ClassLoader loader, String className, 
					Class<?> classRedefined, ProtectionDomain pd, 
					byte[] buff) throws IllegalClassFormatException {
			double diff = (double)(System.currentTimeMillis() - JavaAgent.starttime) / 1000.0;
			//System.out.println(diff + " s > " + className);
			
			String cpath = base + "/" + className + ".class";
			//System.out.println("Testing: " + cpath);
			File f = new File(cpath);
			if(f.isFile()){
				System.out.println("Reading class " + className + " from " + f);
				try{
					return Files.readAllBytes(f.toPath());
				}
				catch(Exception e){
					e.printStackTrace();
				}
			}

			if(className.equals("com/ibm/domino/xsp/module/nsf/ModuleClassLoader")){
				System.out.println("!!!!!!!!! Module class loader found : " + loader + ", " + classRedefined);	
			}
			
			return buff;
		}
	}
}

