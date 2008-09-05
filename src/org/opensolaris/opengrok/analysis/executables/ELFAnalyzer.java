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
 * Copyright 2005 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */
package org.opensolaris.opengrok.analysis.executables;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.logging.Level;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.opensolaris.opengrok.OpenGrokLogger;
import org.opensolaris.opengrok.analysis.FileAnalyzer;
import org.opensolaris.opengrok.analysis.FileAnalyzerFactory;
import org.opensolaris.opengrok.analysis.plain.PlainFullTokenizer;

/**
 * Analyzes ELF (Executable and Linking Format) files.
 * Created on September 23, 2005
 *
 * @author Chandan
 */
public class ELFAnalyzer extends FileAnalyzer {
    private char[] content;
    private int len;
    PlainFullTokenizer plainfull;
    StringReader dummy = new StringReader("");
    
    /**
     * Creates a new instance of ELFAnalyzer
     * @param factory The factory that creates ELFAnalyzers
     */
    protected ELFAnalyzer(FileAnalyzerFactory factory) {
	super(factory);
	content = new char[16*1024];
	plainfull = new PlainFullTokenizer(dummy);
    }
    
    @Override
    public void analyze(Document doc, InputStream in) {
        try {
            if (in instanceof FileInputStream) {
                parseELF((FileInputStream) in);
                if (len > 0) {
                    doc.add(new Field("full", " ", Field.Store.YES, Field.Index.TOKENIZED));
                }
            } else {
                String fullpath = doc.get("fullpath");
                final FileInputStream fin = new FileInputStream(fullpath);
                try {
                    parseELF(fin);
                    if (len > 0) {
                        doc.add(new Field("full", " ", Field.Store.YES, Field.Index.TOKENIZED));
                    }
                } finally {
                    fin.close();
                }
            }
        } catch (IOException ioe) {
                OpenGrokLogger.getLogger().log(Level.WARNING, "Error analyzing ELF file", ioe);
        }
    }

    public void parseELF(FileInputStream f) throws IOException {
	FileChannel fch = f.getChannel();
	MappedByteBuffer fmap = fch.map(FileChannel.MapMode.READ_ONLY, 0, fch.size());
	if(fmap.getInt() != 0x7f454c46) {
            throw new IOException("not an ELF File");
        }
	int shstrtab = 0;
	HashMap<String,Integer> sectionMap = new HashMap<String, Integer>();
	ELFHeader eh;
	ELFSection[] sections;
	eh = new ELFHeader(fmap);
	shstrtab = fmap.getInt(eh.e_shoff + (eh.e_shstrndx) * eh.e_shentsize + 16);
	fmap.position(eh.e_shoff);
	sections = new ELFSection[eh.e_shnum];
	int[] readables = new int[eh.e_shnum];
	int ri = 0;
	for(int i = 0 ; i< eh.e_shnum; i++) {
	    sections[i] = new ELFSection(fmap);
	    String sectionName;
	    if((sectionName = getName(shstrtab, sections[i].sh_name, fmap)) != null) {
		sectionMap.put(sectionName, sections[i].sh_offset);
	    }
	}
	//StringBuilder symTab = new StringBuilder();
	for(int i = 0 ; i< eh.e_shnum; i++) {
	    String sname = getName(shstrtab,sections[i].sh_name, fmap);
	    if(sections[i].sh_type == SHT_STRTAB) {
		readables[ri++] = i;
	    } else if(".debug_str".equals(sname) || ".comment".equals(sname)
	    || ".data".equals(sname) || ".data1".equals(sname)
	    ||".rodata".equals(sname) || ".rodata1".equals(sname)) {
		readables[ri++] = i;
	    }
	}
	boolean lastPrintable = false;
	len = 0;
	for(int i = 0 ; i < ri ; i++) {
	    fmap.position(sections[readables[i]].sh_offset);
	    int size = sections[readables[i]].sh_size;
	    if(len + size > content.length) {
		int max = content.length * 2 + size;
		char[] content2 = new char[max];
		System.arraycopy(content, 0, content2, 0, len);
		content = content2;
	    }
	    byte c;
	    while(size-->0) {
		c = fmap.get();
		if(isReadable(c)) {
		    lastPrintable = true;
		    content[len++] = (char)c;
		} else if(lastPrintable) {
		    lastPrintable = false;
		    content[len++] = ' ';
		}
	    }
	    content[len++] = '\n';
	}
    }
    
    private boolean isReadable(int c) {
	if(c > ' ' && c <= 127) {
	    return true;
	}
	return false;
    }
    
    private String getName(int tab, int stroff, MappedByteBuffer fmap) {
	if(tab == 0) {
            return null;
        }
	StringBuilder sb = new StringBuilder(20);
	byte c;
	int start = tab + stroff;
	while((c = fmap.get(start++)) != 0x00) {
	    sb.append((char)c);
	}
	return sb.toString();
    }
    
    @Override
    public TokenStream tokenStream(String fieldName, Reader reader) {
	if("full".equals(fieldName)) {
	    plainfull.reInit(content,len);
	    return plainfull;
	}
	return super.tokenStream(fieldName, reader);
    }
    
    /**
     * Write a cross referenced HTML file.
     * @param out Writer to write
     */
    @Override
    public void writeXref(Writer out) throws IOException {
	out.write("</pre>");
	out.write(content, 0, len);
	out.write("<pre>");
    }
    
    class ELFHeader {
	// Elf32 Addr = readInt
	// elf32 half = readUnsignedShort
	// Off = int
	// Sword = int
	// Word = int
	// un = unsignedBtye
	public int ei_class;
	public int ei_data;
	public int ei_version;
	
	public int e_type;
	public int e_machine;
	public int e_version;
	public int e_entry;
	public int e_phoff;
	public int e_shoff;
	public int e_flags;
	public int e_ehsize;
	public int e_phentsize;
	public int e_phnum;
	public int e_shentsize;
	public int e_shnum;
	public int e_shstrndx;
	
	public ELFHeader(MappedByteBuffer fmap) {
	    fmap.position(4);
	    ei_class = fmap.get();
	    ei_data = fmap.get();
	    ei_version = fmap.get();
	    if (ei_data == ELFDATA2LSB) {
		fmap.order(ByteOrder.LITTLE_ENDIAN);
	    } else {
		fmap.order(ByteOrder.BIG_ENDIAN);
	    }
	    
	    fmap.position(16);
	    e_type = fmap.getShort();
	    e_machine = fmap.getShort();
	    e_version = fmap.getInt();
	    e_entry = fmap.getInt();
	    e_phoff = fmap.getInt();
	    e_shoff = fmap.getInt();
	    e_flags = fmap.getInt();
	    e_ehsize = fmap.getShort();
	    e_phentsize = fmap.getShort();
	    e_phnum = fmap.getShort();
	    e_shentsize = fmap.getShort();
	    e_shnum = fmap.getShort();
	    e_shstrndx = fmap.getShort();
	}
	
        @Override
	public String toString() {
	    return(EMs[e_machine] + " " +
		ECs[ei_class] + " " +
		"\ne_type: "+e_type+
		"\ne_machine: "+e_machine+
		"\ne_version: "+e_version+
		"\ne_entry: "+e_entry+
		"\ne_phoff: "+e_phoff+
		"\ne_shoff: "+e_shoff+
		"\ne_flags: "+e_flags+
		"\ne_ehsize: "+e_ehsize+
		"\ne_phentsize:"+e_phentsize+
		"\ne_phnum: "+e_phnum+
		"\ne_shentsize"+e_shentsize+
		"\ne_shnum: "+e_shnum+
		"\ne_shstrndx: "+e_shstrndx);
	}
    }
    static class ELFSection {
	public int sh_name;
	public int sh_type;
	public int sh_flags;
	public int sh_addr;
	public int sh_offset;
	public int sh_size;
	public int sh_link;
	public int sh_info;
	public int sh_addralign;
	public int sh_entsize;
	
	public ELFSection(MappedByteBuffer fmap ) {
	    sh_name = fmap.getInt();
	    sh_type = fmap.getInt();
	    sh_flags = fmap.getInt();
	    sh_addr = fmap.getInt();
	    sh_offset = fmap.getInt();
	    sh_size = fmap.getInt();
	    sh_link = fmap.getInt();
	    sh_info = fmap.getInt();
	    sh_addralign = fmap.getInt();
	    sh_entsize = fmap.getInt();
	}
	
        @Override
	public String toString() {
	    return(
		"\nsh_name : " + sh_name +
		"\nsh_type : " + sh_type +
		"\nsh_flags: " + sh_flags +
		"\nsh_addr: " + sh_addr +
		"\nsh_offset: " + sh_offset +
		"\nsh_size: " + sh_size +
		"\nsh_link: " + sh_link +
		"\nsh_info: " + sh_info +
		"\nsh_addralign: " + sh_addralign +
		"\nsh_entsize: " + sh_entsize );
	}
    }

    String[] ECs = {
	"None", "32", "64"
    };
    String[] EMs = {
	"None","AT&T", "SPARC", "Intel 80386",
	    "Motorola 68000", "Motorola 88000", "", "Intel 80860", "MIPS RS3000"
    };
    public static final int SHT_NULL =	0;	/* Section header table entry unused */
    public static final int SHT_PROGBITS =	1;	/* Program data */
    public static final int SHT_SYMTAB =	2;	/* Symbol table */
    public static final int SHT_STRTAB =	3;	/* String table */
    public static final int SHT_RELA =	4;	/* Relocation entries with addends */
    public static final int SHT_HASH =	5;	/* Symbol hash table */
    public static final int SHT_DYNAMIC =	6;	/* Dynamic linking information */
    public static final int SHT_NOTE =	7;	/* Notes */
    public static final int SHT_NOBITS =	8;	/* Program space with no data (bss) */
    public static final int SHT_REL =	9;	/* Relocation entries, no addends */
    public static final int SHT_SHLIB =	10;	/* Reserved */
    public static final int SHT_DYNSYM =	11;	/* Dynamic linker symbol table */
    public static final int SHT_INIT_ARRAY =	14;	/* Array of constructors */
    public static final int SHT_FINI_ARRAY =	15;	/* Array of destructors */
    public static final int SHT_PREINIT_ARRAY =	16;	/* Array of pre-constructors */
    public static final int SHT_GROUP =	17;	/* Section group */
    public static final int SHT_SYMTAB_SHNDX =	18;	/* Extended section indeces */
    public static final int SHT_NUM =	19;	/* Number of defined types.  */
    public static final int SHT_LOOS =	0x60000000;	/* Start OS-specific */
    public static final int SHT_GNU_LIBLIST =	0x6ffffff7;	/* Prelink library list */
    public static final int SHT_CHECKSUM =	0x6ffffff8;	/* Checksum for DSO content.  */
    public static final int SHT_LOSUNW =	0x6ffffffa;	/* Sun-specific low bound.  */
    public static final int SHT_SUNW_COMDAT =	0x6ffffffb;
    public static final int SHT_HISUNW =	0x6fffffff;	/* Sun-specific high bound.  */
    public static final int SHT_HIOS =	0x6fffffff;	/* End OS-specific type */
    public static final int SHT_LOPROC =	0x70000000;	/* Start of processor-specific */
    public static final int SHT_HIPROC =	0x7fffffff;	/* End of processor-specific */
    public static final int SHT_LOUSER =	0x80000000;	/* Start of application-specific */
    public static final int SHT_HIUSER =	0x8fffffff;	/* End of application-specific */
    
    public static final int ELFDATA2LSB =	1;	/* 2's complement, little endian */
}
