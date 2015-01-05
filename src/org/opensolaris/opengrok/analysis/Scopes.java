/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opensolaris.opengrok.analysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author kotal
 */
public class Scopes implements Serializable {
    private static final long serialVersionUID = 1191703801007779489L;
    
    public static class Scope implements Serializable {
        private static final long serialVersionUID = 1191703801007779489L;

        public int lineFrom;
        public int lineTo;
        public String name;
        public String scope;

        public Scope(int lineFrom, int lineTo, String name, String scope) {
            this.lineFrom = lineFrom;
            this.lineTo = lineTo;
            this.name = name;
            this.scope = scope;
        }
        
        public String getName() {
            return name; //(scope == null ? name : scope + "::" + name) + "()";
        }
    }
    
    private static Scope globalScope = new Scope(0, 0, "global", "");
    
    private List<Scope> scopes = new ArrayList<>();  
    
    public Scopes() {
        
    }
    
    public void addScope(Scope scope) {
        scopes.add(scope);
    }
    
    public Scope getScope(int line) {
        for (Scope s : scopes) {
            if (s.lineFrom <= line && s.lineTo >= line) {
                return s;
            }
        }
        
        return globalScope;
    }
    
    /**
     * Create a binary representation of this object.
     * @return a byte array representing this object
     * @throws IOException if an error happens when writing to the array
     */
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(this);
        return bytes.toByteArray();
    }

    /**
     * Deserialize a binary representation of a {@code Definitions} object.
     * @param bytes a byte array containing the {@code Definitions} object
     * @return a {@code Definitions} object
     * @throws IOException if an I/O error happens when reading the array
     * @throws ClassNotFoundException if the class definition for an object
     * stored in the byte array cannot be found
     * @throws ClassCastException if the array contains an object of another
     * type than {@code Definitions}
     */
    public static Scopes deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(bytes));
        return (Scopes) in.readObject();
    }
}
