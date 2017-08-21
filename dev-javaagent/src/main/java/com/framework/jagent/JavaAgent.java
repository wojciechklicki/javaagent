package com.framework.jagent;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.utility.JavaModule;

import static net.bytebuddy.matcher.ElementMatchers.*;


public class JavaAgent {
	static long starttime = System.currentTimeMillis();

	private static final String base = "C:/Users/Dysant Workstation/odk_workspace/FPA/WebContent/WEB-INF/classes";
	// private static final String base =
	// "C:/Users/Administrator/eclipse-workspace/FPA/WebContent/WEB-INF/classes";
	
	public static class SimpleHandler{
		@Advice.OnMethodEnter
		public String toString2() {
			System.out.println("toString test");
			return "dziala !!!";
		}
	}
	
	public static void premain(String args, Instrumentation instrumentation) {
		System.out.println("Adding class folder " + base);
		File cbase = new File(base);
		if (!cbase.isDirectory()) {
			System.err.println("Class folder does not exits for current application");
		} else {
			System.out.println("Class folder found");
		}
		//ClassHandler tr = new ClassHandler();
		//instrumentation.addTransformer(tr);

		new AgentBuilder.Default().type(named("com.ibm.domino.xsp.module.nsf.ModuleClassLoader"))
			.transform(new AgentBuilder.Transformer() {
				@Override
					public Builder<?> transform(Builder<?> builder, TypeDescription tdesc, ClassLoader cl, JavaModule arg3) {
						return builder.method(named("toString2")).intercept(Advice.to(SimpleHandler.class));
					}
		}).installOn(instrumentation);
	}

	public static class ClassHandler implements ClassFileTransformer {
		@Override
		public byte[] transform(ClassLoader loader, String className, Class<?> classRedefined, ProtectionDomain pd,
				byte[] buff) throws IllegalClassFormatException {
			double diff = (double) (System.currentTimeMillis() - JavaAgent.starttime) / 1000.0;
			System.out.println(diff + " s > " + className);

			String cpath = base + "/" + className + ".class";
			// System.out.println("Testing: " + cpath);
			File f = new File(cpath);
			if (f.isFile()) {
				// System.out.println("Reading class " + className + " from " + f);
				try {
					// return Files.readAllBytes(f.toPath());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if (className.equals("com/ibm/domino/xsp/module/nsf/ModuleClassLoader")) {
				System.out.println("!!!!!!!!! Module class loader found : " + loader + ", " + classRedefined);
			}

			return buff;
		}
	}
}
