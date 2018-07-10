package com.framework.jagent;

import static net.bytebuddy.matcher.ElementMatchers.named;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
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

import com.framework.WorkspaceScanner;

public class JavaAgent {
	private static final String CNAME_DYNAMIC_CLASS_LOADER = "com.ibm.domino.xsp.module.nsf.ModuleClassLoader$DynamicClassLoader";
	private static final String CNAME_MODULE_CLASS_LOADER = "com.ibm.domino.xsp.module.nsf.ModuleClassLoader";

	static boolean DEBUG = false;
	
	static long starttime = System.currentTimeMillis();
	private static List<WorkspaceScanner.Project> projects = Collections.emptyList();
	private static Map<String, List<String>> projectsClassFoldersByDb = new HashMap<String, List<String>>();

	private static File findProjectFile(List<String> cfolders, String fname) {
		for(String cfolder:cfolders) {
			String cpath = cfolder + "/" + fname;
			if (DEBUG) {
				System.out.println("Searching for " + cpath);
			}
			File f = new File(cpath);
			if(f.exists()) {
				return f;
			}
		}
		return null;
	}

	private static String computeFileNameFromClassName(String name) {
		return name.replace(".", "/");
	}
	
	public static class ClassLoaderProxy {		
		@RuntimeType
		public static Object getResources(@Argument(0) String name, @SuperCall Callable<Class<?>> superMethod,
				@Origin Method origin, @This Object self) throws Exception {
			System.out.println("!!! getResources: " + name);
			return superMethod.call();
		}
		
		@RuntimeType
		public static Object getResource(@Argument(0) String name, @SuperCall Callable<Class<?>> superMethod,
				@Origin Method origin, @This Object self) throws Exception {
			System.out.println("getResource for project: " + name);
			ClassLoader cl = (ClassLoader) self;
			boolean isDynamicClassLoader = (cl instanceof SecureClassLoader);
			String moduleName = computeModuleName(cl, isDynamicClassLoader);
			
			if(!JavaAgent.projectsClassFoldersByDb.containsKey(moduleName)) {
				if(DEBUG) {
					System.out.println("No config found for project " + moduleName);
				}
				return superMethod.call();
			}
			
			File f = findProjectFile(JavaAgent.projectsClassFoldersByDb.get(moduleName), name);
			if (f != null) {
				System.out.println("Reading resource url " + name + " from " + f);
				return f.toURI().toURL();
			} else {
				return superMethod.call();
			}
		}
		
		@RuntimeType
		public static Object getResourceAsStream(@Argument(0) String name, @SuperCall Callable<Class<?>> superMethod,
				@Origin Method origin, @This Object self) throws Exception {
			System.out.println("getResourceAsStream for project: " + name);
			ClassLoader cl = (ClassLoader) self;
			boolean isDynamicClassLoader = (cl instanceof SecureClassLoader);
			String moduleName = computeModuleName(cl, isDynamicClassLoader);
			
			if(!JavaAgent.projectsClassFoldersByDb.containsKey(moduleName)) {
				if(DEBUG) {
					System.out.println("No config found for project " + moduleName);
				}
				return superMethod.call();
			}
			
			File f = findProjectFile(JavaAgent.projectsClassFoldersByDb.get(moduleName), name);
			if (f != null) {
				System.out.println("Reading resource url " + name + " from " + f);
				return f.toURI().toURL().openStream();
			} else {
				return superMethod.call();
			}
		}
		
		@RuntimeType
		public static Class<?> findClass(@Argument(0) String name, @SuperCall Callable<Class<?>> superMethod,
				@Origin Method origin, @This Object self) throws Exception {
			ClassLoader cl = (ClassLoader) self;
			boolean isDynamicClassLoader = (cl instanceof SecureClassLoader);
			String moduleName = computeModuleName(cl, isDynamicClassLoader);
			if(!JavaAgent.projectsClassFoldersByDb.containsKey(moduleName)) {
				if(DEBUG) {
					System.out.println("No config found for project " + moduleName);
				}
				return superMethod.call();
			}
			
			File f = findProjectFile(JavaAgent.projectsClassFoldersByDb.get(moduleName), computeFileNameFromClassName(name) + ".class");
			if (f != null) {
				System.out.println("Reading class " + name + " from " + f);
				byte[] code = FileUtils.readFileToByteArray(f);
				if (isDynamicClassLoader) {
					Class<?>[] SecureDefineParams = new Class<?>[] { String.class, byte[].class, int.class, int.class,
							CodeSource.class };
					Method defClass = SecureClassLoader.class.getDeclaredMethod("defineClass", SecureDefineParams);
					defClass.setAccessible(true);
					return (Class<?>) defClass.invoke(cl, name, code, 0, code.length, null);
				} else {
					Class<?>[] NormalDefineParams = new Class<?>[] { String.class, byte[].class, int.class, int.class };
					Method defClass = ClassLoader.class.getDeclaredMethod("defineClass", NormalDefineParams);
					defClass.setAccessible(true);
					return (Class<?>) defClass.invoke(cl, name, code, 0, code.length);
				}
			} else {
				return superMethod.call();
			}
		}

		private static String computeModuleName(ClassLoader cl, boolean isDynamicClassLoader)
				throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
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
			return moduleName;
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
	}

	private static final String DEBUG_PREFIX = "debug;";
	
	public static void premain(String args, Instrumentation instrumentation) {
		if (args == null) {
			System.err.println("No workspace folder found in args");
			return;
		}
		if(args.startsWith(DEBUG_PREFIX)) {
			JavaAgent.DEBUG = true;
			args = args.substring(DEBUG_PREFIX.length());
		}
		
		WorkspaceScanner ws = new WorkspaceScanner();
		
		try {
			JavaAgent.projects = ws.searchForProjects(args);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		for(WorkspaceScanner.Project project: JavaAgent.projects) {
			System.out.println("Loading project " + project);
			for(String db: project.getDatabasePaths()) {
				if(!JavaAgent.projectsClassFoldersByDb.containsKey(db)) {
					JavaAgent.projectsClassFoldersByDb.put(db, new ArrayList<String>());
				}
				List<String> list = JavaAgent.projectsClassFoldersByDb.get(db);
				String cf = project.getClassFolderPath();
				System.out.println("Adding class folder " + cf);
				list.add(cf);
			}
		}
		
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
						return builder.method(named("findClass").or(named("getResource").or(named("getResources")).or(named("getResourceAsStream"))))
								.intercept(MethodDelegation.to(ClassLoaderProxy.class));
					}
				}).installOn(instrumentation);
	}
}
