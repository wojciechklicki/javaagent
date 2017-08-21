import com.ibm.domino.xsp.module.nsf.*;

public class Example {
	public static void main(String[] args) {
		System.out.println("Running app");
		ModuleClassLoader m = new ModuleClassLoader();
		System.out.println("ToString: " + m.toString2());
	}
}
