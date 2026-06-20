package fastsoftware3d;

import fastsoftware3d.fastsoftware3d;

/**
 * Basic Hello World Demo for fastsoftware3d.
 */
public class Demo {
    public static void main(String[] args) {
        System.out.println("=== fastsoftware3d Demo ===");
        
        fastsoftware3d api = new fastsoftware3d();
        
        System.out.println("Calling native method...");
        api.doSomethingNative();
        
        System.out.println("=== Demo Complete ===");
    }
}
