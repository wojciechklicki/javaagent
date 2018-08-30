package com.framework.jagent;

import static net.bytebuddy.matcher.ElementMatchers.named;
import net.bytebuddy.matcher.ElementMatchers;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.Callable;

import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

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
import net.bytebuddy.asm.Advice;

import java.util.zip.ZipEntry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import com.framework.WorkspaceScanner;

public class JavaAgent {
	private static final String CNAME_DYNAMIC_CLASS_LOADER = "com.ibm.domino.xsp.module.nsf.ModuleClassLoader$DynamicClassLoader";
	private static final String CNAME_MODULE_CLASS_LOADER = "com.ibm.domino.xsp.module.nsf.ModuleClassLoader";

	public static boolean DEBUG = true;
	// debug all - including byte buddy byte code modifications
	public static boolean DEBUG_ALL = false;
	static long starttime = System.currentTimeMillis();
	private static List<WorkspaceScanner.Project> projects = Collections.emptyList();
	private static Map<String, ProjectResources> projectResources = new HashMap<String, ProjectResources>();

	private static class JarInfo{
		public List<String> fileList = new ArrayList<String>();
		public JarFile jarFile;
		public String path;

		public void reload(){
			if(DEBUG){
				System.out.println("Reloading jar " + this.path);
			}
			this.fileList.addAll(this.findFilesInJar(new File(this.path)));
		}

		public JarInfo(File f){
			this.fileList.addAll(this.findFilesInJar(f));
			this.path = f.getAbsolutePath();
			if(DEBUG_ALL){
				System.out.println("Jar loaded: " + this.path + " files:");
				System.out.println(this.fileList);
			}
		}

		public InputStream getContentStream(String fileName){
			try{
				ZipEntry ze = jarFile.getEntry(fileName);
				return this.jarFile.getInputStream(ze);
			}
			catch(Exception e){
				e.printStackTrace();
			}
			return null;
		}

		public byte [] getFileContent(String fileName){
			InputStream is = null;
			try{
				ZipEntry ze = jarFile.getEntry(fileName);
				is = this.jarFile.getInputStream(ze);
				return IOUtils.toByteArray(is);
			}
			catch(IOException e){
				if(is != null){
					try{
						is.close();
					}
					catch(Exception e2){
						e2.printStackTrace();
					}
				}
			}
			return null;
		}

		public boolean containsFile(String fileName){
			return this.fileList.contains(fileName);
		}

		private List<String> findFilesInJar(File jar){
			List<String> classFiles = new ArrayList<String>();
			try{
				this.jarFile = new JarFile(jar);
				final Enumeration<JarEntry> entries = this.jarFile.entries();
				while (entries.hasMoreElements()) {
					final JarEntry entry = entries.nextElement();
					final String entryName = entry.getName();
					classFiles.add(entryName);
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
			return classFiles;
		}
	}

	private static class ProjectFile{
		boolean insideJar = false;
		JarInfo jarInfo;
		String filePath;
		public URL url;

		public ProjectFile(File f){
			try{
				this.url = f.toURI().toURL();
				this.filePath = f.getAbsolutePath();
			}catch(Exception e){
				throw new RuntimeException(e);
			}
		}

		public File getFile(){
			return new File(this.filePath);
		}
		
		public InputStream getStream(){
			try{
				if(insideJar){
					return this.jarInfo.getContentStream(this.filePath);
				}
				else{
					return new File(this.filePath).toURI().toURL().openStream();
				}
			}
			catch(Exception e){
				throw new RuntimeException(e);
			}
		}

		public String toString(){
			return this.url.toString();
		}

		public ProjectFile(JarInfo jr, String fname){
			this.insideJar = true;
			this.jarInfo = jr;
			this.filePath = fname;
			try{
				this.url = new URL("file://" + jr.path + "!" + fname); 
			}
			catch(Exception e){
				throw new RuntimeException(e);
			}
		} 

	}

	private static class ProjectResources{
		public List<String> classPaths = new ArrayList<String>();
		public List<JarInfo> jars = new ArrayList<JarInfo>();
	
		public void reset(){
			System.out.println("Resetting project cache");
			for(JarInfo ji : jars){
				ji.reload();
			}
		}

		public void addCP(String classPath){
			this.classPaths.add(classPath);
		}

		private ProjectFile findProjectFile(String fname) {
			for(String cfolder: this.classPaths) {
				String cpath = cfolder + "/" + fname;
				if (DEBUG) {
					System.out.println("Searching for " + cpath);
				}
				File f = new File(cpath);
				if(f.exists()) {
					return new ProjectFile(f);
				}
			}
			for(JarInfo jr: this.jars){
				if(jr.containsFile(fname)){
					//return new ProjectFile(jr, fname);
				}
			}

			return null;
		}

		public void addJar(File f){
			JarInfo ji = new JarInfo(f);
			jars.add(ji);
		}

	}


	private static String computeFileNameFromClassName(String name) {
		return name.replace(".", "/");
	}

	public static class TimerAdvice {
	  	@Advice.OnMethodEnter
	    static long enter() {
			return System.currentTimeMillis();
		}

		@Advice.OnMethodExit()
		static void exit(@Advice.Origin String method, @Advice.Enter long start) {
			System.out.println(method + " took " + (System.currentTimeMillis() - start));
		}
	}


	public static class ClassLoaderProxy {	
		@RuntimeType
		public static Object getResources(@Argument(0) String name, @SuperCall Callable<Class<?>> superMethod,
				@Origin Method origin, @This Object self) throws Exception {
			// System.out.println("getResources: " + name);
			ClassLoader cl = (ClassLoader) self;
			boolean isDynamicClassLoader = (cl instanceof SecureClassLoader);
			String moduleName = computeModuleName(cl, isDynamicClassLoader);
			
			
			if(!JavaAgent.projectResources.containsKey(moduleName)) {
				if(DEBUG) {
					System.out.println("No config found for project " + moduleName);
				}
				return superMethod.call();
			}
			
			ProjectFile f = JavaAgent.projectResources.get(moduleName).findProjectFile(name);
			if (f != null) {
				// System.out.println("Reading resource url " + name + " from " + f);
				Vector<URL> res = new Vector<URL>();
				for(File cf: f.getFile().listFiles()) {
					res.addElement(cf.toURI().toURL());
				}
				Enumeration<URL> en = res.elements();
				return en;
			} else {
				return superMethod.call();
			}
		}
	

		@RuntimeType
		public static void verifySignature(@Argument(0) Object resource, @SuperCall Callable<Class<?>> superMethod,
				@Origin Method origin, @This Object self) throws Exception {
		}




		@RuntimeType
		public static void setSignerSessionRights(@Argument(0) String resource, @SuperCall Callable<Class<?>> superMethod,
				@Origin Method origin, @This Object self) throws Exception {

				try{
					String signer = "CN=Wojciech Klicki/O=dysant";

					//System.out.println("Processing resource: " + resource);
					Field topSigner = self.getClass().getDeclaredField("toplevelXPageSigner");
					topSigner.setAccessible(true);
					if(topSigner.get(self) == null){
					//	System.out.println("Setting top signer: " + signer);
						topSigner.set(self, signer);
						Field signers = self.getClass().getDeclaredField("checkedSigners");
						signers.setAccessible(true);
						HashSet<String> defSigners = new HashSet<String>();
						defSigners.add(signer);
						signers.set(self, defSigners);
					}
					else{
					//	System.out.println("Signer already present:" + topSigner.get(self));
					}

				}
				catch(Exception e){
					e.printStackTrace();
					throw new java.lang.RuntimeException(e);
				}
		}

		@RuntimeType
		public static Object getResource(@Argument(0) String name, @SuperCall Callable<Class<?>> superMethod,
				@Origin Method origin, @This Object self) throws Exception {
			// System.out.println("getResource for project: " + name);
			ClassLoader cl = (ClassLoader) self;
			boolean isDynamicClassLoader = (cl instanceof SecureClassLoader);
			String moduleName = computeModuleName(cl, isDynamicClassLoader);
			
			if(!JavaAgent.projectResources.containsKey(moduleName)) {
				if(DEBUG) System.out.println("No config found for project " + moduleName);
				return superMethod.call();
			}
			
			ProjectFile f = JavaAgent.projectResources.get(moduleName).findProjectFile(name);
			if (f != null) {
				// System.out.println("Reading resource url " + name + " from " + f);
				return f.url;
			} else {
				return superMethod.call();
			}
		}
		
		@RuntimeType
		public static Object getResourceAsStream(@Argument(0) String name, @SuperCall Callable<Class<?>> superMethod,
				@Origin Method origin, @This Object self) throws Exception {
			// System.out.println("getResourceAsStream for project: " + name);
			ClassLoader cl = (ClassLoader) self;
			boolean isDynamicClassLoader = (cl instanceof SecureClassLoader);
			String moduleName = computeModuleName(cl, isDynamicClassLoader);
			
			if(!JavaAgent.projectResources.containsKey(moduleName)) {
				if(DEBUG) {
					System.out.println("No config found for project " + moduleName);
				}
				return superMethod.call();
			}
			
			ProjectFile f = JavaAgent.projectResources.get(moduleName).findProjectFile(name);
			if (f != null) {
				// System.out.println("Reading resource url " + name + " from " + f);
				return f.getStream();
			} else {
				return superMethod.call();
			}
		}


		@RuntimeType
		public static void resetDynamicClassLoader(@SuperCall Callable<Class<?>> superMethod, @Origin Method origin, @This Object self) throws Exception{
			ClassLoader cl = (ClassLoader) self;
			boolean isDynamicClassLoader = (cl instanceof SecureClassLoader);
			String moduleName = computeModuleName(cl, isDynamicClassLoader);
			if(DEBUG) System.out.println("resetting dynamic class loader: " + moduleName);

			ProjectResources pr = JavaAgent.projectResources.get(moduleName);
			if(pr != null){
				pr.reset();
			}
			superMethod.call();
		}
		
		@RuntimeType
		public static Class<?> findClass(@Argument(0) String name, @SuperCall Callable<Class<?>> superMethod, @Origin Method origin, @This Object self) throws Exception{
			if(DEBUG) System.out.println("findClass: " + name);
			Class c = _loadClass(name, false, superMethod, origin, self);
			if(c == null){
				return superMethod.call();
			}
			return c;
		}

		@RuntimeType
		public static Class<?> loadClass(@Argument(0) String name, @Argument(1) Boolean resolveClass, @SuperCall Callable<Class<?>> superMethod, @Origin Method origin, @This Object self) throws Exception {	
			if(DEBUG) System.out.println("loadClass: " + name + ", " + resolveClass);
			Class c = _loadClass(name, resolveClass, superMethod, origin, self);
			if(c == null){	
				return superMethod.call();
			}
			return c;
		}

		@RuntimeType
		public static Class<?> loadClass(@Argument(0) String name, @SuperCall Callable<Class<?>> superMethod, @Origin Method origin, @This Object self) throws Exception {
			if(DEBUG) System.out.println("loadClass: " + name);
			Class c = _loadClass(name, false, superMethod, origin, self);
			if(c == null){
				return superMethod.call();
			}
			else{
				return c;
			}
		}

		public static Class<?> _loadClass(String name, Boolean resolve, Callable<Class<?>> superMethod, Method origin, Object self) throws Exception{
			ClassLoader cl = (ClassLoader) self;
			boolean isDynamicClassLoader = (cl instanceof SecureClassLoader);
			String moduleName = computeModuleName(cl, isDynamicClassLoader);

			if(name.equals("@isDevMode")){
				if(JavaAgent.projectResources.containsKey(moduleName)){
					return Boolean.class;
				}
				else{
					return null;
				}
			}

			if(!JavaAgent.projectResources.containsKey(moduleName)) {
				if(DEBUG) {
					System.out.println("No config found for project " + moduleName);
				}
				return null;
			}
			
			ProjectFile f = JavaAgent.projectResources.get(moduleName).findProjectFile(computeFileNameFromClassName(name) + ".class");
			if (f != null) {
				if(DEBUG){
					System.out.println("Reading class " + name + " from " + f);
				}
				byte[] code = IOUtils.toByteArray(f.getStream());
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
				return null;
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
		System.out.println("Premain - java agent running");
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
				if(!JavaAgent.projectResources.containsKey(db)) {
					JavaAgent.projectResources.put(db, new ProjectResources());
				}
				ProjectResources pr = JavaAgent.projectResources.get(db);
				String cf = project.getClassFolderPath();
				System.out.println("Adding class folder " + cf);
				pr.addCP(cf);
				for(String resPath : project.getResourcesPaths()) {
					System.out.println("Adding resource path " + resPath);
					pr.addCP(resPath);
				}
				for(String jar: project.getJars()){
					//System.out.println("Adding jar " + jar);
					//pr.addJar(new File(jar));
				}
			}
		}
		
		AgentBuilder ab = new AgentBuilder.Default();
		if (DEBUG_ALL) {
			// method delegation error logging to console
			ab = ab.with(AgentBuilder.Listener.StreamWriting.toSystemOut());
		}
		
		/*
		ab.type(ElementMatchers.nameEndsWith("NotesContext"))
				.transform(new AgentBuilder.Transformer() {
					@Override
					public Builder<?> transform(Builder<?> builder, TypeDescription tdesc, ClassLoader cl,
								JavaModule arg3){
						      return builder.visit(Advice.to(TimerAdvice.class).on(ElementMatchers.named("verifySignature")));
					}
				});
		*/
		
		ab.type(named(CNAME_MODULE_CLASS_LOADER).or(named(CNAME_DYNAMIC_CLASS_LOADER).or(named("com.ibm.domino.xsp.module.nsf.NotesContext"))))
				.transform(new AgentBuilder.Transformer() {
					@Override
					public Builder<?> transform(Builder<?> builder, TypeDescription tdesc, ClassLoader cl,
							JavaModule arg3) {
						return builder.method(named("findClass")
//						return builder.method(named("loadClass")
								.or(named("getResource")
								.or(named("resetDynamicClassLoader"))
								.or(named("getResources"))
								.or(named("verifySignature"))
								.or(named("setSignerSessionRights"))
								.or(named("getResourceAsStream"))))
								.intercept(MethodDelegation.to(ClassLoaderProxy.class));
					}
				}).installOn(instrumentation);
	}
}
