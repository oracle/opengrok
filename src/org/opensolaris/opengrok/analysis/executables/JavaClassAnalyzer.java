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
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis.executables;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantCP;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantDouble;
import org.apache.bcel.classfile.ConstantFloat;
import org.apache.bcel.classfile.ConstantInteger;
import org.apache.bcel.classfile.ConstantInvokeDynamic;
import org.apache.bcel.classfile.ConstantLong;
import org.apache.bcel.classfile.ConstantMethodHandle;
import org.apache.bcel.classfile.ConstantMethodType;
import org.apache.bcel.classfile.ConstantModule;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPackage;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantString;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Utility;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.IteratorReader;
import org.opensolaris.opengrok.analysis.OGKTextField;
import org.opensolaris.opengrok.analysis.OGKTextVecField;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.search.QueryBuilder;
import org.opensolaris.opengrok.web.Util;

/**
 * Analyzes Java Class files Created on September 23, 2005
 *
 * @author Chandan
 * @author Lubos Kosco , January 2010 , updated bcel, comment on thread safety
 */
public class JavaClassAnalyzer extends FileAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaClassAnalyzer.class);

    private final String urlPrefix = RuntimeEnvironment.getInstance().getUrlPrefix();

    /**
     * Creates a new instance of JavaClassAnalyzer
     *
     * @param factory The factory that creates JavaClassAnalyzers
     */
    protected JavaClassAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
    }

    /**
     * Gets a version number to be used to tag processed documents so that
     * re-analysis can be re-done later if a stored version number is different
     * from the current implementation.
     * @return 20180112_00
     */
    @Override
    protected int getSpecializedVersionNo() {
        return 20180112_00; // Edit comment above too!
    }

    @Override
    public void analyze(Document doc, StreamSource src, Writer xrefOut) throws IOException {
        try (InputStream in = src.getStream()) {
            analyze(doc, in, xrefOut);
        }
    }

    void analyze(Document doc, InputStream in, Writer xrefOut) throws IOException {
        List<String> defs = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        List<String> full = new ArrayList<>();

        StringWriter dout = new StringWriter();
        StringWriter rout = new StringWriter();
        StringWriter fout = new StringWriter();

        /**
         * The JarAnalyzer uses JavaClassAnalyzer, so if a DEFS, REFS, or FULL
         * field exists already, then append to it.
         */
        useExtantValue(dout, doc, QueryBuilder.DEFS);
        useExtantValue(rout, doc, QueryBuilder.REFS);
        useExtantValue(fout, doc, QueryBuilder.FULL);

        ClassParser classparser = new ClassParser(in,
            doc.get(QueryBuilder.PATH));
        StringWriter xout = new StringWriter();
        getContent(xout, fout, classparser.parse(), defs, refs, full);
        String xref = xout.toString();

        if (xrefOut != null) {
            xrefOut.append(xref);
            try { 
                xrefOut.flush();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Couldn't flush xref, will retry once added to doc", ex);
            }
        }        

        appendValues(dout, defs, "");
        appendValues(rout, refs, "");
        appendValues(fout, full, "// ");

        /**
         * Unlike other analyzers, which rely on the full content existing to be
         * accessed at a file system location identified by PATH, *.class and
         * *.jar files have virtual content which is stored here (Store.YES) for
         * analyzer convenience.
         */

        String dstr = dout.toString();
        doc.add(new OGKTextField(QueryBuilder.DEFS, dstr, Store.YES));

        String rstr = rout.toString();
        doc.add(new OGKTextField(QueryBuilder.REFS, rstr, Store.YES));

        String fstr = fout.toString();
        doc.add(new OGKTextField(QueryBuilder.FULL, fstr, Store.YES));
    }

    
    private static final String AHREF="<a href=\"";
    private static final String AHREFT_END="\">";
    private static final String AHREFEND="</a>";
    private static final String ADEFS="defs=";
    private static final String APATH="path=";
    private static final String AIHREF="\" href=\"";
    private static final String ADHREF="<a class=\"d\" name=\"";
    private final StringBuffer rstring=new StringBuffer(512);
    protected String linkPath(String path) {
        rstring.setLength(0);
        return rstring.append(AHREF).append(urlPrefix).append(APATH)
                .append(Util.URIEncodePath(path)).append(AHREFT_END)
                .append(Util.htmlize(path)).append(AHREFEND).toString();
    }

    protected String linkDef(String def) {
        rstring.setLength(0);
        return rstring.append(AHREF).append(urlPrefix).append(ADEFS)
                .append(Util.URIEncode(def)).append(AHREFT_END)
                .append(Util.htmlize(def)).append(AHREFEND).toString();
    }

    protected String tagDef(String def) {
        // The fragment identifiers in HTML 4 must start with a letter, so
        // <init> (for constructors) or <clinit> (for class initializers)
        // cannot be used as identifiers. Also the $ character in inner
        // classes is not allowed. Strip away such characters for now.
        // HTML 5 does not have these restrictions.
        String name = def.replaceAll("[<>$]", "");

        rstring.setLength(0);
        return rstring.append(ADHREF).append(Util.formQuoteEscape(name))
                .append(AIHREF).append(urlPrefix).append(ADEFS)
                .append(Util.URIEncode(def)).append(AHREFT_END)
                .append(Util.htmlize(def)).append(AHREFEND).toString();
    }

private static final String PACKAGE="package ";
private static final char EOL='\n';
private static final char TAB='\t';
private static final char SPACE=' ';
private static final String EXTENDS=" extends ";
private static final String IMPLEMENTS=" implements ";
private static final String THROWS=" throws ";
private static final String THIS="this";
private static final String LCBREOL=" {\n";
private static final String LBRA=" (";
private static final String COMMA=", ";
private static final String RBRA=") ";
private static final String RCBREOL="}\n";
    
//TODO this class needs to be thread safe to avoid bug 13364, which was fixed by just updating bcel to 5.2
    private void getContent(Writer out, Writer fout, JavaClass c,
            List<String> defs, List<String> refs, List<String> full)
            throws IOException {
        String t;
        ConstantPool cp = c.getConstantPool();
        
        
        int[] v = new int[cp.getLength() + 1];
        out.write(linkPath(t = c.getSourceFileName()));
        defs.add(t);
        refs.add(t);
        fout.write(t);
        out.write(EOL);
        fout.write(EOL);

        out.write(PACKAGE);
        fout.write(PACKAGE);
        out.write(linkDef(t = c.getPackageName()));
        defs.add(t);
        refs.add(t);
        fout.write(t);

        out.write(EOL);
        fout.write(EOL);
        String aflg;
        out.write(aflg = Utility.accessToString(c.getAccessFlags(), true));        
        if (aflg != null) {            
            out.write(SPACE);
            fout.write(aflg);
            fout.write(SPACE);
        }

        v[c.getClassNameIndex()] = 1;
        out.write(tagDef(t = c.getClassName()));
        defs.add(t);
        refs.add(t);
        fout.write(t);
        out.write(EXTENDS);
        fout.write(EXTENDS);

        v[c.getSuperclassNameIndex()] = 1;
        out.write(linkDef(t = c.getSuperclassName()));
        refs.add(t);
        fout.write(t);
        for (int i : c.getInterfaceIndices()) {
            v[i] = 1;
        }
        String ins[] = c.getInterfaceNames();
        if (ins != null && ins.length > 0) {
            out.write(IMPLEMENTS);
            fout.write(IMPLEMENTS);
            for (String in : ins) {
                out.write(linkDef(t = in));
                refs.add(t);
                fout.write(t);
                out.write(SPACE);
                fout.write(SPACE);
            }
        }
        out.write(LCBREOL);
        fout.write(LCBREOL);

        for (Attribute a : c.getAttributes()) {
            if (a.getTag() == org.apache.bcel.Const.ATTR_CODE) {
                for (Attribute ca : ((Code) a).getAttributes()) {
                    if (ca.getTag() == org.apache.bcel.Const.ATTR_LOCAL_VARIABLE_TABLE) {
                        for (LocalVariable l : ((LocalVariableTable) ca).getLocalVariableTable()) {
                            printLocal(out, fout, l, v, defs, refs);
                        }
                    }
                }
            } else if   (a.getTag() == org.apache.bcel.Const.ATTR_BOOTSTRAP_METHODS ) {
                // TODO fill in bootstrap methods, fix the else if
            } else if (a.getTag() == org.apache.bcel.Const.ATTR_SOURCE_FILE) {
                v[a.getNameIndex()] = 1;
                break;
            }
        }

        String aflgs;
        String fldsig;
        String tdef;
        for (org.apache.bcel.classfile.Field fld : c.getFields()) {
            out.write(TAB);
            fout.write(TAB);
            aflgs = Utility.accessToString(fld.getAccessFlags());            
            if (aflgs != null && aflgs.length() > 0) {
                out.write(aflgs);
                fout.write(aflgs);
                fout.write(SPACE);
                out.write(SPACE);
            }
            fldsig=Utility.signatureToString(fld.getSignature());
            out.write(fldsig);
            fout.write(fldsig);
            out.write(SPACE);
            fout.write(SPACE);
            tdef=tagDef(t = fld.getName());
            out.write(tdef);
            fout.write(tdef);
            defs.add(t);
            refs.add(t);
            out.write(EOL);
            fout.write(EOL);
            //TODO show Attributes
        }

        String sig;
        String msig;
        String ltdef;
        for (org.apache.bcel.classfile.Method m : c.getMethods()) {
            out.write(TAB);
            fout.write(TAB);
            aflgs = Utility.accessToString(m.getAccessFlags());            
            if (aflgs != null && aflgs.length() > 0) {
                out.write(aflgs);
                fout.write(aflgs);
                out.write(SPACE);
                fout.write(SPACE);
            }
            sig = m.getSignature();
            msig=Utility.methodSignatureReturnType(sig, false);
            out.write(msig);
            fout.write(msig);
            out.write(SPACE);
            fout.write(SPACE);
            ltdef=tagDef(t = m.getName());
            out.write(ltdef);
            fout.write(ltdef);
            defs.add(t);
            refs.add(t);
            out.write(LBRA);
            fout.write(LBRA);
            String[] args = Utility.methodSignatureArgumentTypes(sig, false);
            for (int i = 0; i < args.length; i++) {
                t = args[i];
                out.write(t);
                fout.write(t);
                int spi = t.indexOf(SPACE);
                if (spi > 0) {
                    refs.add(t.substring(0, spi));
                    defs.add(t.substring(spi + 1));
                }
                if (i < args.length - 1) {
                    out.write(COMMA);
                    fout.write(COMMA);
                }
            }
            out.write(RBRA);
            fout.write(RBRA);
            ArrayList<LocalVariable[]> locals = new ArrayList<>();
            for (Attribute a : m.getAttributes()) {
                if (a.getTag() == org.apache.bcel.Const.ATTR_EXCEPTIONS) {
                    for (int i : ((ExceptionTable) a).getExceptionIndexTable()) {
                        v[i] = 1;
                    }
                    String[] exs = ((ExceptionTable) a).getExceptionNames();
                    if (exs != null && exs.length > 0) {
                        out.write(THROWS);
                        fout.write(THROWS);
                        for (String ex : exs) {
                            out.write(linkDef(ex));
                            fout.write(ex);
                            refs.add(ex);
                            out.write(SPACE);
                            fout.write(SPACE);
                        }
                    }
                } else if (a.getTag() == org.apache.bcel.Const.ATTR_CODE) {
                    for (Attribute ca : ((Code) a).getAttributes()) {
                        if (ca.getTag() == org.apache.bcel.Const.ATTR_LOCAL_VARIABLE_TABLE) {
                            locals.add(((LocalVariableTable) ca).getLocalVariableTable());
                        }
                    }
                }
            }
            out.write(EOL);
            fout.write(EOL);
            if (!locals.isEmpty()) {
                for (LocalVariable[] ls : locals) {
                    for (LocalVariable l : ls) {
                        printLocal(out, fout, l, v, defs, refs);
                    }
                }
            }
        }
        out.write(RCBREOL);
        fout.write(RCBREOL);
        for (int i = 0; i < v.length - 1; i++) {
            if (v[i] != 1) {
                Constant constant = cp.getConstant(i);
                if (constant != null) {
                    full.add(constantToString(constant, cp, v));
                }
            }
        }
    }

    private void printLocal(Writer out, Writer fout, LocalVariable l,
            int[] v, List<String> defs, List<String> refs) throws IOException {
        v[l.getIndex()] = 1;
        v[l.getNameIndex()] = 1;
        v[l.getSignatureIndex()] = 1;
        if (!THIS.equals(l.getName())) {
            out.write(TAB);out.write(TAB);
            fout.write(TAB);fout.write(TAB);
            String sig=Utility.signatureToString(l.getSignature());
            out.write(sig);
            fout.write(sig);
            out.write(SPACE);
            fout.write(SPACE);
            String t;
            out.write(t = l.getName());
            defs.add(t);
            refs.add(t);
            fout.write(t);
            out.write(EOL);
            fout.write(EOL);
        }
    }

    public String constantToString(Constant c, ConstantPool cp, int[] v)
            throws ClassFormatException {
        String str;
        int i, j, k;
        byte tag = c.getTag();

        switch (tag) {
            case org.apache.bcel.Const.CONSTANT_Class:
                i = ((ConstantClass) c).getNameIndex();
                v[i] = 1;
                Constant con = cp.getConstant(i, org.apache.bcel.Const.CONSTANT_Utf8);
                str = Utility.compactClassName(((ConstantUtf8) con).getBytes(), false);
                break;

            case org.apache.bcel.Const.CONSTANT_String:
                i = ((ConstantString) c).getStringIndex();
                v[i] = 1;
                Constant con2 = cp.getConstant(i, org.apache.bcel.Const.CONSTANT_Utf8);
                str = ((ConstantUtf8) con2).getBytes();
                break;

            case org.apache.bcel.Const.CONSTANT_Utf8:
                str = ((ConstantUtf8) c).toString();
                break;
            case org.apache.bcel.Const.CONSTANT_Double:
                str = ((ConstantDouble) c).toString();
                break;
            case org.apache.bcel.Const.CONSTANT_Float:
                str = ((ConstantFloat) c).toString();
                break;
            case org.apache.bcel.Const.CONSTANT_Long:
                str = ((ConstantLong) c).toString();
                break;
            case org.apache.bcel.Const.CONSTANT_Integer:
                str = ((ConstantInteger) c).toString();
                break;

            case org.apache.bcel.Const.CONSTANT_NameAndType:
                i = ((ConstantNameAndType) c).getNameIndex();
                v[i] = 1;
                j = ((ConstantNameAndType) c).getSignatureIndex();
                v[j] = 1;
                String sig = constantToString(cp.getConstant(j), cp, v);
                if (sig.charAt(0) == '(') {
                    str = Utility.methodSignatureToString(sig,
                            constantToString(cp.getConstant(i), cp, v), " ");
                } else {
                    str = Utility.signatureToString(sig) + ' ' +
                            constantToString(cp.getConstant(i), cp, v);
                }
                //str = constantToString(cp.getConstant(i)) +' ' + sig;

                break;

            case org.apache.bcel.Const.CONSTANT_InterfaceMethodref:
            case org.apache.bcel.Const.CONSTANT_Methodref:
            case org.apache.bcel.Const.CONSTANT_Fieldref:
                i = ((ConstantCP) c).getClassIndex();
                v[i] = 1;
                j = ((ConstantCP) c).getNameAndTypeIndex();
                v[j] = 1;
                str = (constantToString(cp.getConstant(i), cp, v) + ' ' +
                        constantToString(cp.getConstant(j), cp, v));
                break;
                
            case org.apache.bcel.Const.CONSTANT_MethodType:
                i = ((ConstantMethodType) c).getDescriptorIndex();
                v[i] = 1;                
                str = constantToString(cp.getConstant(i), cp, v);
                break;
//                CONSTANT_MethodType_info {
//    u1 tag;
//    u2 descriptor_index;
//}
            case org.apache.bcel.Const.CONSTANT_MethodHandle:
    //TODO fix implementation as per below to have proper lookup table/constants
//                i = ((ConstantMethodHandle) c).getReferenceKind();
//                v[i] = 1;
//                j = ((ConstantMethodHandle) c).getReferenceIndex();
//                v[j] = 1;
//                str = (constantToString(cp.getConstant(i), cp, v) + ' ' +
//                        constantToString(cp.getConstant(j), cp, v));
                str="";
                break;
//                CONSTANT_MethodHandle_info {
//    u1 tag;
//    u1 reference_kind;
//    u2 reference_index;
//}
            case org.apache.bcel.Const.CONSTANT_InvokeDynamic:
    //TODO fix implementation as per below and add bootstrap method tables first
//                i = ((ConstantInvokeDynamic) c).getClassIndex();
//                v[i] = 1;
//                j = ((ConstantInvokeDynamic) c).getNameAndTypeIndex();
//                v[j] = 1;
//                k = ((ConstantInvokeDynamic) c).getBootstrapMethodAttrIndex();
//                v[k] = 1;
//                str = (constantToString(cp.getConstant(i), cp, v) + ' ' +
//                        constantToString(cp.getConstant(j), cp, v) + ' ' +
//                        constantToString(cp.getConstant(k), cp, v));
                str="";
                break;            
            case org.apache.bcel.Const.CONSTANT_Package:    
                i = ((ConstantPackage) c).getNameIndex();
                v[i] = 1;                
                str = constantToString(cp.getConstant(i), cp, v);
                break;
            case org.apache.bcel.Const.CONSTANT_Module:
                i = ((ConstantModule) c).getNameIndex();
                v[i] = 1;                
                str = constantToString(cp.getConstant(i), cp, v);
                break;
                
//                CONSTANT_InvokeDynamic_info {
//    u1 tag;
//    u2 bootstrap_method_attr_index;
//    u2 name_and_type_index;
//}
                
// if types are missing add more as per
// https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4.2 
// and bcel docs 
// https://commons.apache.org/proper/commons-bcel/apidocs/org/apache/bcel/classfile/Constant.html  
                
            default: // Never reached
                throw new ClassFormatException("Unknown constant type " + tag);
        }
        return str;
    }

    private static void useExtantValue(StringWriter accum, Document doc,
        String field) {
        String extantValue = doc.get(field);
        if (extantValue != null) {
            doc.removeFields(field);
            accum.append(extantValue);
        }
    }

    private static void appendValues(StringWriter accum, List<String> full,
        String lede) {
        for (String fl : full) {
            accum.write(lede);
            accum.write(fl);
            accum.write(EOL);
        }
    }
}
