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
 * Copyright 2009 Sun Microsystems, Inc.  All rights reserved.
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
import org.opensolaris.opengrok.web.Util;

/**
 * Analyzes ELF (Executable and Linking Format) files.
 * Created on September 23, 2005
 *
 * @author Chandan
 * @author Trond Norbye
 */
public class ELFAnalyzer extends FileAnalyzer {

    private StringBuilder content;
    PlainFullTokenizer plainfull;
    StringReader dummy = new StringReader("");

    /**
     * Creates a new instance of ELFAnalyzer
     * @param factory The factory that creates ELFAnalyzers
     */
    protected ELFAnalyzer(FileAnalyzerFactory factory) {
        super(factory);
        content = new StringBuilder();
        plainfull = new PlainFullTokenizer(dummy);
    }

    @Override
    public void analyze(Document doc, InputStream in) throws IOException {
        if (in instanceof FileInputStream) {
            parseELF((FileInputStream) in);
            if (content.length() > 0) {
                doc.add(new Field("full", " ", Field.Store.YES, Field.Index.ANALYZED));
            }
        } else {
            String fullpath = doc.get("fullpath");
            final FileInputStream fin = new FileInputStream(fullpath);
            try {
                parseELF(fin);
                if (content.length() > 0) {
                    doc.add(new Field("full", " ", Field.Store.YES, Field.Index.ANALYZED));
                }
            } finally {
                fin.close();
            }
        }
    }

    public void parseELF(FileInputStream f) throws IOException {
        FileChannel fch = f.getChannel();
        MappedByteBuffer fmap = fch.map(FileChannel.MapMode.READ_ONLY, 0, fch.size());
        ELFHeader eh = new ELFHeader(fmap);

        if (eh.e_shnum <= 0) {
            OpenGrokLogger.getLogger().log(Level.FINE, "Skipping file, no section headers");
            return;
        }

        fmap.position(eh.e_shoff + (eh.e_shstrndx * eh.e_shentsize));
        ELFSection stringSection = new ELFSection(fmap);

        if (stringSection.sh_size == 0) {
            OpenGrokLogger.getLogger().log(Level.FINE, "Skipping file, no section name string table");
            return ;
        }

        HashMap<String, Integer> sectionMap = new HashMap<String, Integer>();
        ELFSection[] sections = new ELFSection[eh.e_shnum];
        int[] readables = new int[eh.e_shnum];

        int ri = 0;
        for (int i = 0; i < eh.e_shnum; i++) {
            fmap.position(eh.e_shoff + (i * eh.e_shentsize));

            sections[i] = new ELFSection(fmap);
            String sectionName;
            if ((sectionName = getName(stringSection.sh_offset, sections[i].sh_name, fmap)) != null) {
                sectionMap.put(sectionName, sections[i].sh_offset);
            }

            if (sections[i].sh_type == ELFSection.SHT_STRTAB) {
                readables[ri++] = i;
            } else if (".debug_str".equals(sectionName) || ".comment".equals(sectionName)
                    || ".data".equals(sectionName) || ".data1".equals(sectionName)
                    || ".rodata".equals(sectionName) || ".rodata1".equals(sectionName)) {
                readables[ri++] = i;
            }
        }

        boolean lastPrintable = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ri; i++) {
            fmap.position(sections[readables[i]].sh_offset);
            int size = sections[readables[i]].sh_size;
            byte c;
            while (size-- > 0) {
                c = fmap.get();
                if (isReadable(c)) {
                    lastPrintable = true;
                    sb.append((char) c);
                } else if (lastPrintable) {
                    lastPrintable = false;
                    sb.append(' ');
                }
            }
            sb.append('\n');
        }
        sb.trimToSize();
        content = sb;
    }
    
    private boolean isReadable(int c) {
        if (c > ' ' && c <= 127) {
            return true;
        }
        return false;
    }

    private String getName(int tab, int stroff, MappedByteBuffer fmap) {
        if (tab == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(20);
        byte c;
        int start = tab + stroff;
        while ((c = fmap.get(start++)) != 0x00) {
            sb.append((char) c);
        }
        return sb.toString();
    }

    @Override
    public TokenStream tokenStream(String fieldName, Reader reader) {
        if ("full".equals(fieldName)) {
            char[] cs = new char[content.length()];
            content.getChars(0, cs.length, cs, 0);
            plainfull.reInit(cs, cs.length);
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
        String html = Util.htmlize(content);
        out.write(html);
        out.write("<pre>");
    }

    private static class ELFHeader {
        // Elf32 Addr = readInt
        // elf32 half = readUnsignedShort
        // Off = int
        // Sword = int
        // Word = int
        // un = unsignedBtye

        public EI_Class ei_class;
        public EI_Data ei_data;
        @SuppressWarnings("unused")
        public int ei_version;
        public E_Type e_type;
        public E_Machine e_machine;
        public E_Version e_version;
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

        public ELFHeader(MappedByteBuffer fmap) throws IllegalArgumentException {
            if (fmap.get(ELFIdentification.EI_MAG0.value()) != 0x7f ||
                fmap.get(ELFIdentification.EI_MAG1.value()) != 'E' ||
                fmap.get(ELFIdentification.EI_MAG2.value()) != 'L' ||
                fmap.get(ELFIdentification.EI_MAG3.value()) != 'F') {
                throw new IllegalArgumentException("Not an ELF file");
            }

            ei_class = EI_Class.valueOf(fmap.get(ELFIdentification.EI_CLASS.value()));
            ei_data = EI_Data.valueOf(fmap.get(ELFIdentification.EI_DATA.value()));
            ei_version = fmap.get(ELFIdentification.EI_VERSION.value());

            if (ei_data == EI_Data.ELFDATA2LSB) {
                fmap.order(ByteOrder.LITTLE_ENDIAN);
            } else {
                fmap.order(ByteOrder.BIG_ENDIAN);
            }

            fmap.position(ELFIdentification.EI_NIDENT.value());
            e_type = E_Type.valueOf(fmap.getShort());
            e_machine = E_Machine.valueOf(fmap.getShort());
            e_version = E_Version.valueOf(fmap.getInt());
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
            return (e_machine.toString() + " " + ei_class.toString() + " " + "\ne_type: " + e_type.toString() + "\ne_machine: " + e_machine.value() + "\ne_version: " + e_version + "\ne_entry: " + e_entry + "\ne_phoff: " + e_phoff + "\ne_shoff: " + e_shoff + "\ne_flags: " + e_flags + "\ne_ehsize: " + e_ehsize + "\ne_phentsize:" + e_phentsize + "\ne_phnum: " + e_phnum + "\ne_shentsize" + e_shentsize + "\ne_shnum: " + e_shnum + "\ne_shstrndx: " + e_shstrndx);
        }
    }

    private static class ELFSection {
        public static final int SHT_NULL =      0;      /* Section header table entry unused */
        public static final int SHT_PROGBITS =  1;      /* Program data */
        public static final int SHT_SYMTAB =    2;      /* Symbol table */
        public static final int SHT_STRTAB =    3;      /* String table */
        public static final int SHT_RELA =      4;      /* Relocation entries with addends */
        public static final int SHT_HASH =      5;      /* Symbol hash table */
        public static final int SHT_DYNAMIC =   6;      /* Dynamic linking information */
        public static final int SHT_NOTE =      7;      /* Notes */
        public static final int SHT_NOBITS =    8;      /* Program space with no data (bss) */
        public static final int SHT_REL =       9;      /* Relocation entries, no addends */
        public static final int SHT_SHLIB =     10;     /* Reserved */
        public static final int SHT_DYNSYM =    11;     /* Dynamic linker symbol table */
        public static final int SHT_INIT_ARRAY =        14;     /* Array of constructors */
        public static final int SHT_FINI_ARRAY =        15;     /* Array of destructors */
        public static final int SHT_PREINIT_ARRAY =     16;     /* Array of pre-constructors */
        public static final int SHT_GROUP =     17;     /* Section group */
        public static final int SHT_SYMTAB_SHNDX =      18;     /* Extended section indeces */
        public static final int SHT_NUM =       19;     /* Number of defined types.  */
        public static final int SHT_LOOS =      0x60000000;     /* Start OS-specific */
        public static final int SHT_GNU_LIBLIST =       0x6ffffff7;     /* Prelink library list */
        public static final int SHT_CHECKSUM =  0x6ffffff8;     /* Checksum for DSO content.  */
        public static final int SHT_LOSUNW =    0x6ffffffa;     /* Sun-specific low bound.  */
        public static final int SHT_SUNW_COMDAT =       0x6ffffffb;
        public static final int SHT_HISUNW =    0x6fffffff;     /* Sun-specific high bound.  */
        public static final int SHT_HIOS =      0x6fffffff;     /* End OS-specific type */
        public static final int SHT_LOPROC =    0x70000000;     /* Start of processor-specific */
        public static final int SHT_HIPROC =    0x7fffffff;     /* End of processor-specific */
        public static final int SHT_LOUSER =    0x80000000;     /* Start of application-specific */
        public static final int SHT_HIUSER =    0x8fffffff;     /* End of application-specific */

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

        public ELFSection(MappedByteBuffer fmap) {
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
            return ("\nsh_name : " + sh_name + "\nsh_type : " + sh_type + "\nsh_flags: " + sh_flags + "\nsh_addr: " + sh_addr + "\nsh_offset: " + sh_offset + "\nsh_size: " + sh_size + "\nsh_link: " + sh_link + "\nsh_info: " + sh_info + "\nsh_addralign: " + sh_addralign + "\nsh_entsize: " + sh_entsize);
        }
    }

    private static enum ELFIdentification {

        EI_MAG0(0),
        EI_MAG1(1),
        EI_MAG2(2),
        EI_MAG3(3),
        EI_CLASS(4),
        EI_DATA(5),
        EI_VERSION(6),
        EI_PAD(7),
        EI_NIDENT(16);
        private final int value;

        private ELFIdentification(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }
    }

    private static enum EI_Class {
        ELFCLASSNONE(0),
        ELFCLASS32(1),
        ELFCLASS64(2);

        final String[] textual = {
            "None", "32", "64"
        };

        private final int value;

        private EI_Class(int value) {
            this.value = value;
        }

        static EI_Class valueOf(byte value) throws IllegalArgumentException {
            switch (value) {
                case 0: return ELFCLASSNONE;
                case 1: return ELFCLASS32;
                case 2: return ELFCLASS64;
                default:
                    throw new IllegalArgumentException("Invalid EI_CLASS value:" + value);
            }
        }

        public int value() {
            return this.value;
        }

        @Override
        public String toString() {
            return textual[value];
        }
    }

    private static enum EI_Data {
        ELFDATANONE(0),
        ELFDATA2LSB(1),
        ELFDATA2MSB(2);

        private final int value;

        private EI_Data(int value) {
            this.value = value;
        }

        static EI_Data valueOf(byte value) throws IllegalArgumentException {
            switch (value) {
                case 0: return ELFDATANONE;
                case 1: return ELFDATA2LSB;
                case 2: return ELFDATA2MSB;
                default:
                    throw new IllegalArgumentException("Invalid EI_DATA value:" + value);
            }
        }

        public int value() {
            return this.value;
        }
    }

    private static enum E_Type {
        ET_NONE(0),
        ET_REL(1),
        ET_EXEC(2),
        ET_DYN(3),
        ET_CORE(4),
        ET_UNKNOWN(0xFFFF);

        final String[] textual = {
            "None", "Relocable", "Executable", "Shared object", "Core"
        };

        private final int value;

        private E_Type(int value) {
            this.value = value;
        }

        static E_Type valueOf(short value) {
            switch (value) {
                case 0: return ET_NONE;
                case 1: return ET_REL;
                case 2: return ET_EXEC;
                case 3: return ET_DYN;
                case 4: return ET_CORE;
                default:
                    return ET_UNKNOWN;
            }
        }

        public int value() {
            return this.value;
        }

        @Override
        public String toString() {
            if (value == ET_UNKNOWN.value()) {
                return "Unknown";
            }
            return textual[value];
        }
    }

    private static enum E_Machine {
        EM_NONE(0),
        EM_M32(1),
        EM_SPARC(2),
        EM_386(3),
        EM_68K(4),
        EM_88K(5),
        EM_860(7),
        EM_MIPS(8),
        EM_UNKNOWN(0xFFFF);

        final String[] textual = {
            "No machine", "AT&T WE 32100", "SPARC", "Intel 80386",
            "Motorola 68000", "Motorola 88000", null,
            "Intel 80860", "MIPS RS3000"
        };

        private final int value;

        private E_Machine(int value) {
            this.value = value;
        }

        static E_Machine valueOf(short value) {
            switch (value) {
                case 0: return EM_NONE;
                case 1: return EM_M32;
                case 2: return EM_SPARC;
                case 3: return EM_386;
                case 4: return EM_68K;
                case 5: return EM_88K;
                case 7: return EM_860;
                case 8: return EM_MIPS;
                default:
                    return EM_UNKNOWN;
            }
        }

        public int value() {
            return this.value;
        }

        @Override
        public String toString() {
            if (value == EM_UNKNOWN.value()) {
                return "Unknown";
            }
            return textual[value];
        }
    }

    private static enum E_Version {
        EV_NONE(0),
        EV_CURRENT(1);

        final String[] textual = {
            "Invalid", "Current"
        };

        private final int value;

        private E_Version(int value) {
            this.value = value;
        }

        static E_Version valueOf(int value) throws IllegalArgumentException {
            switch (value) {
                case 0: return EV_NONE;
                case 1: return EV_CURRENT;
                default:
                    throw new IllegalArgumentException("Illegal (or unknown) version number: " + value);
            }
        }

        public int value() {
            return this.value;
        }

        @Override
        public String toString() {
            return textual[value];
        }
    }
}
