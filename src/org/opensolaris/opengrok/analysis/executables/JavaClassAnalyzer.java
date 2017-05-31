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
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.analysis.executables;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
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
import org.apache.bcel.classfile.ConstantLong;
import org.apache.bcel.classfile.ConstantNameAndType;
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
import org.apache.lucene.document.TextField;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.IteratorReader;
import org.opensolaris.opengrok.analysis.StreamSource;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.logger.LoggerFactory;
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

        ClassParser classparser = new ClassParser(in, doc.get("path"));
        StringWriter out = new StringWriter();
        StringWriter fout = new StringWriter();
        getContent(out, fout, classparser.parse(), defs, refs, full);
        String fullt = fout.toString();
        String xref = out.toString();

        if (xrefOut != null) {
            xrefOut.append(xref);
            try { 
                xrefOut.flush();
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Couldn't flush xref, will retry once added to doc", ex);
            }
        }        
        xref = null; //flush the xref        

        StringWriter cout=new StringWriter();
        for (String fl : full) {
            cout.write(fl);
            cout.write('\n');
        }
        String constants = cout.toString();
        
        StringReader fullout=new StringReader(fullt);

        doc.add(new TextField("defs", new IteratorReader(defs)));
        doc.add(new TextField("refs", new IteratorReader(refs)));
        doc.add(new TextField("full", fullout));
        doc.add(new TextField("full", constants, Store.NO));
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
        int i, j;
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

            default: // Never reached
                throw new ClassFormatException("Unknown constant type " + tag);
        }
        return str;
    }
}
