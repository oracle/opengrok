/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis.java;

public class Sample {
    
    static private String MY_MEMBER = "value";
    
    public Sample() {
        
    }
    
    public int Method(int arg) {
        int res = 5;
        
        res += arg;
        
        InnerClass i = new InnerClass();
        
        return i.InnerMethod().length() * res;
    }

    public abstract int AbstractMethod(int test);
    
    private class InnerClass {
        
        public String InnerMethod() {
            // somthing } */
            /* }}} 
                multi-line comment }{}
            */
            
            System.out.print("I'm so useless");
            
            return "Why do robots need to drink?";
        }
        
    }

    public static void main(String args[]) {
        int num1, num2;
        try { 
            // Try block to handle code that may cause exception
            num1 = 0;
            num2 = 62 / num1;
            System.out.println("Try block message");
        } catch (ArithmeticException e) { 
            // This block is to catch divide-by-zero error
            System.out.println("Error: Don't divide a number by zero");
        }
        System.out.println("I'm out of try-catch block in Java.");
    }
    
}
