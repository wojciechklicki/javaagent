import com.ibm.domino.xsp.module.nsf.*;

public class Example {
	public static void main(String[] args) {
		System.out.println("Running app");
		ModuleClassLoader m = new ModuleClassLoader();
		Class cls = m.findClass("test.class");
		System.out.println("Class found: " + cls);
		System.out.println("Classloader: " + cls.getClassLoader());
		Class cls2 = m.findClass("some.class");
		System.out.println("Class found: " + cls2);
		System.out.println("Classloader: " + cls2.getClassLoader());
	}
}
