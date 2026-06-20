package fastXXX;

import fastXXX.FastXXX;

/**
 * Basic Hello World Demo for FastXXX.
 */
public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FastXXX Demo ===");
        
        FastXXX api = new FastXXX();
        
        System.out.println("Calling native method...");
        api.doSomethingNative();
        
        System.out.println("=== Demo Complete ===");
    }
}
