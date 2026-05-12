package com.bvb.sharedmodule;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Run once to generate the BCrypt hash for the seed admin password.
 * mvn -pl shared-module test-compile exec:java -Dexec.mainClass=com.bvb.sharedmodule.GenerateBcryptHash -Dexec.classpathScope=test
 */
public class GenerateBcryptHash {
    public static void main(String[] args) {
        String password = args.length > 0 ? args[0] : "Admin@1234";
        System.out.println(new BCryptPasswordEncoder(10).encode(password));
    }
}
