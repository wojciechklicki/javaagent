package com.framework.jagent;

import java.io.File;
import java.util.concurrent.Callable;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.lang.reflect.Method;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.utility.JavaModule;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.Argument;
import static net.bytebuddy.matcher.ElementMatchers.*;


public class JavaAgent {
	static long starttime = System.currentTimeMillis();

	private static final String base = "C:/Users/Dysant Workstation/odk_workspace/FPA/WebContent/WEB-INF/classes";
	// private static final String base =
	// "C:/Users/Administrator/eclipse-workspace/FPA/WebContent/WEB-INF/classes";
	
	public static class SimpleHandler{
		@RuntimeType		
		public static Class findClass(@Argument(0) String name, @SuperCall Callable<Class> superMethod, @Origin Method origin, @This Object self) throws Exception{
			System.out.println("Origin: " + origin);
			System.out.println("This: " + self);
			if(name.contains("test")){
				return superMethod.call();
			}
			else{
				System.out.println("new findClass for " + name);
				return SimpleHandler.class;
			}
		}
	}
	
	public static void premain(String args, Instrumentation instrumentation) {
		System.out.println("Adding class folder: " + base);
		File cbase = new File(base);
		if (!cbase.isDirectory()) {
			System.err.println("Class folder does not exits for current application");
		} else {
			System.out.println("Class folder found");
		}
		//ClassHandler tr = new ClassHandler();
		//instrumentation.addTransformer(tr);
		AgentBuilder ab = new AgentBuilder.Default();
		//method delegation error logging to console
		//ab = ab.with(AgentBuilder.Listener.StreamWriting.toSystemOut());
		ab.type(named("com.ibm.domino.xsp.module.nsf.ModuleClassLoader"))
			.transform(new AgentBuilder.Transformer() {
				@Override
					public Builder<?> transform(Builder<?> builder, TypeDescription tdesc, ClassLoader cl, JavaModule arg3) {
						return builder.method(named("findClass")).intercept(MethodDelegation.to(SimpleHandler.class));
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
