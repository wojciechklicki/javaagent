package com.framework.jagent;

import static net.bytebuddy.matcher.ElementMatchers.named;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.utility.JavaModule;

public class JavaAgent {
	private static final String CNAME_DYNAMIC_CLASS_LOADER = "com.ibm.domino.xsp.module.nsf.ModuleClassLoader$DynamicClassLoader";
	private static final String CNAME_MODULE_CLASS_LOADER = "com.ibm.domino.xsp.module.nsf.ModuleClassLoader";

	static boolean DEBUG = false;
	
	static long starttime = System.currentTimeMillis();

	private static Map<String, String> projectMap = new HashMap<String, String>();
	
	public static class ClassLoaderProxy {
		@RuntimeType
		public static Class findClass(@Argument(0) String name, @SuperCall Callable<Class> superMethod,
				@Origin Method origin, @This Object self) throws Exception {
			ClassLoader cl = (ClassLoader) self;
			boolean isDynamicClassLoader = (cl instanceof SecureClassLoader);
			String moduleName = null;
			if (isDynamicClassLoader) {
				Field field = cl.getClass().getDeclaredField("this$0");
				field.setAccessible(true);

				// Dereference and cast it
				Object moduleClassLoader = field.get(cl);
				moduleName = getModuleName(moduleClassLoader);
			}
			else {
				moduleName = getModuleName(cl);
			}
			if(!JavaAgent.projectMap.containsKey(moduleName)) {
				System.out.println("No config found for project " + moduleName);
				return superMethod.call();
			}
			
			String cpath = JavaAgent.projectMap.get(moduleName) + "/" + computeFileNameFromClassName(name) + ".class";
			if (DEBUG) {
				System.out.println("Searching for " + cpath);
			}
			File f = new File(cpath);
			if (f.isFile()) {
				System.out.println("Reading class " + name + " from " + f);
				byte[] code = FileUtils.readFileToByteArray(f);
				if (isDynamicClassLoader) {
					Class[] SecureDefineParams = new Class[] { String.class, byte[].class, int.class, int.class,
							CodeSource.class };
					Method defClass = SecureClassLoader.class.getDeclaredMethod("defineClass", SecureDefineParams);
					defClass.setAccessible(true);
					return (Class) defClass.invoke(cl, name, code, 0, code.length, null);
				} else {
					Class[] NormalDefineParams = new Class[] { String.class, byte[].class, int.class, int.class };
					Method defClass = ClassLoader.class.getDeclaredMethod("defineClass", NormalDefineParams);
					defClass.setAccessible(true);
					return (Class) defClass.invoke(cl, name, code, 0, code.length);
				}
			} else {
				return superMethod.call();
			}
		}

		private static String getModuleName(Object moduleClassLoader)
				throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
			Field moduleField = moduleClassLoader.getClass().getDeclaredField("module");
			moduleField.setAccessible(true);
			
			Object module = moduleField.get(moduleClassLoader);
			Method getModuleName = module.getClass().getMethod("getModuleName");
			String mname = (String)getModuleName.invoke(module);
			
			return mname;
		}

		private static String computeFileNameFromClassName(String name) {
			return name.replace(".", "/");
		}
	}

	public static void premain(String args, Instrumentation instrumentation) {
		if (args == null) {
			System.err.println("No base folder found in args");
			return;
		}
		for(String pconf : args.split(";")) {
			if(!pconf.contains("=")) {
				System.err.println("Wrong config: " + pconf);
				continue;
			}
			String[] confTab = pconf.split("=");
			JavaAgent.projectMap.put(confTab[0], confTab[1]);
		}
		
		System.out.println("Adding class folders: " + JavaAgent.projectMap);

		AgentBuilder ab = new AgentBuilder.Default();
		if (DEBUG) {
			// method delegation error logging to console
			ab = ab.with(AgentBuilder.Listener.StreamWriting.toSystemOut());
		}
		ab.type(named(CNAME_MODULE_CLASS_LOADER).or(named(CNAME_DYNAMIC_CLASS_LOADER)))
				.transform(new AgentBuilder.Transformer() {
					@Override
					public Builder<?> transform(Builder<?> builder, TypeDescription tdesc, ClassLoader cl,
							JavaModule arg3) {
						return builder.method(named("findClass"))
								.intercept(MethodDelegation.to(ClassLoaderProxy.class));
					}
				}).installOn(instrumentation);
	}
}
