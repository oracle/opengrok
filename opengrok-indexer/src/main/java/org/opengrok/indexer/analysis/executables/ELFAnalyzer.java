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
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2018, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.analysis.executables;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.opengrok.indexer.analysis.AnalyzerFactory;
import org.opengrok.indexer.analysis.FileAnalyzer;
import org.opengrok.indexer.analysis.OGKTextField;
import org.opengrok.indexer.analysis.StreamSource;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.search.QueryBuilder;
import org.opengrok.indexer.web.Util;

/**
 * Analyzes ELF (Executable and Linking Format) files.
 * Created on September 23, 2005
 *
 * @author Chandan
 * @author Trond Norbye
 */
public class ELFAnalyzer extends FileAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ELFAnalyzer.class);

    private static final List<String> READABLE_SECTIONS;
    static {
        READABLE_SECTIONS = new ArrayList<>();
        READABLE_SECTIONS.add(".debug_str");
        READABLE_SECTIONS.add(".comment");
        READABLE_SECTIONS.add(".data");
        READABLE_SECTIONS.add(".data1");
        READABLE_SECTIONS.add(".rodata");
        READABLE_SECTIONS.add(".rodata1");
    }

    /**
     * Creates a new instance of ELFAnalyzer.
     * @param factory The factory that creates ELFAnalyzers
     */
    protected ELFAnalyzer(AnalyzerFactory factory) {
        super(factory);
    }

    /**
     * @return {@code null} as there is no aligned language
     */
    @Override
    public String getCtagsLang() {
        return null;
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
        String fullpath = doc.get("fullpath");
        String content;
        try (RandomAccessFile raf = new RandomAccessFile(fullpath, "r")) {
            content = parseELF(raf.getChannel());
        }

        if (content != null && !content.isEmpty()) {
            doc.add(new OGKTextField(QueryBuilder.FULL, content, Store.NO));
            if (xrefOut != null) {
                xrefOut.append("</pre>");
                Util.htmlize(content, xrefOut);
                xrefOut.append("<pre>");
            }
        }
    }

    public String parseELF(FileChannel fch) throws IOException {
        MappedByteBuffer fmap = fch.map(FileChannel.MapMode.READ_ONLY, 0, fch.size());
        ELFHeader eh = new ELFHeader(fmap);

        if (eh.e_shnum <= 0) {
            LOGGER.log(Level.FINE, "Skipping file, no section headers");
            return null;
        }

        fmap.position(eh.e_shoff + (eh.e_shstrndx * eh.e_shentsize));
        ELFSection stringSection = new ELFSection(fmap);

        if (stringSection.sh_size == 0) {
            LOGGER.log(Level.FINE, "Skipping file, no section name string table");
            return null;
        }

        ELFSection[] sections = new ELFSection[eh.e_shnum];
        int[] readables = new int[eh.e_shnum];

        int ri = 0;
        for (int i = 0; i < eh.e_shnum; i++) {
            fmap.position(eh.e_shoff + (i * eh.e_shentsize));

            sections[i] = new ELFSection(fmap);
            String sectionName = getName(stringSection.sh_offset, sections[i].sh_name, fmap);

            if (sections[i].sh_type == ELFSection.SHT_STRTAB) {
                readables[ri++] = i;
            } else if (READABLE_SECTIONS.contains(sectionName)) {
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
        return sb.toString();
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

    private static class ELFHeader {
        // Elf32 Addr = readInt
        // elf32 half = readUnsignedShort
        // Off = int
        // Sword = int
        // Word = int
        // un = unsignedBtye

        EI_Class ei_class;
        EI_Data ei_data;
        @SuppressWarnings("unused")
        int ei_version;
        E_Type e_type;
        E_Machine e_machine;
        E_Version e_version;
        int e_entry;
        int e_phoff;
        int e_shoff;
        int e_flags;
        int e_ehsize;
        int e_phentsize;
        int e_phnum;
        int e_shentsize;
        int e_shnum;
        int e_shstrndx;

        ELFHeader(MappedByteBuffer fmap) throws IllegalArgumentException {
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
            return (e_machine.toString() + " " + ei_class.toString() + " " + "\ne_type: " + e_type.toString() +
                    "\ne_machine: " + e_machine.value() + "\ne_version: " + e_version + "\ne_entry: " + e_entry +
                    "\ne_phoff: " + e_phoff + "\ne_shoff: " + e_shoff + "\ne_flags: " + e_flags +
                    "\ne_ehsize: " + e_ehsize + "\ne_phentsize:" + e_phentsize + "\ne_phnum: " + e_phnum +
                    "\ne_shentsize" + e_shentsize + "\ne_shnum: " + e_shnum + "\ne_shstrndx: " + e_shstrndx);
        }
    }

    private static class ELFSection {
        static final int SHT_NULL =      0;      /* Section header table entry unused */
        static final int SHT_PROGBITS =  1;      /* Program data */
        static final int SHT_SYMTAB =    2;      /* Symbol table */
        static final int SHT_STRTAB =    3;      /* String table */
        static final int SHT_RELA =      4;      /* Relocation entries with addends */
        static final int SHT_HASH =      5;      /* Symbol hash table */
        static final int SHT_DYNAMIC =   6;      /* Dynamic linking information */
        static final int SHT_NOTE =      7;      /* Notes */
        static final int SHT_NOBITS =    8;      /* Program space with no data (bss) */
        static final int SHT_REL =       9;      /* Relocation entries, no addends */
        static final int SHT_SHLIB =     10;     /* Reserved */
        static final int SHT_DYNSYM =    11;     /* Dynamic linker symbol table */
        static final int SHT_INIT_ARRAY =        14;     /* Array of constructors */
        static final int SHT_FINI_ARRAY =        15;     /* Array of destructors */
        static final int SHT_PREINIT_ARRAY =     16;     /* Array of pre-constructors */
        static final int SHT_GROUP =     17;     /* Section group */
        static final int SHT_SYMTAB_SHNDX =      18;     /* Extended section indices */
        static final int SHT_NUM =       19;     /* Number of defined types.  */
        static final int SHT_LOOS =      0x60000000;     /* Start OS-specific */
        static final int SHT_GNU_LIBLIST =       0x6ffffff7;     /* Prelink library list */
        static final int SHT_CHECKSUM =  0x6ffffff8;     /* Checksum for DSO content.  */
        static final int SHT_LOSUNW =    0x6ffffffa;     /* Sun-specific low bound.  */
        static final int SHT_SUNW_COMDAT =       0x6ffffffb;
        static final int SHT_HISUNW =    0x6fffffff;     /* Sun-specific high bound.  */
        static final int SHT_HIOS =      0x6fffffff;     /* End OS-specific type */
        static final int SHT_LOPROC =    0x70000000;     /* Start of processor-specific */
        static final int SHT_HIPROC =    0x7fffffff;     /* End of processor-specific */
        static final int SHT_LOUSER =    0x80000000;     /* Start of application-specific */
        static final int SHT_HIUSER =    0x8fffffff;     /* End of application-specific */

        int sh_name;
        int sh_type;
        int sh_flags;
        int sh_addr;
        int sh_offset;
        int sh_size;
        int sh_link;
        int sh_info;
        int sh_addralign;
        int sh_entsize;

        ELFSection(MappedByteBuffer fmap) {
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
            return ("\nsh_name : " + sh_name + "\nsh_type : " + sh_type + "\nsh_flags: " + sh_flags +
                    "\nsh_addr: " + sh_addr + "\nsh_offset: " + sh_offset + "\nsh_size: " + sh_size +
                    "\nsh_link: " + sh_link + "\nsh_info: " + sh_info + "\nsh_addralign: " + sh_addralign +
                    "\nsh_entsize: " + sh_entsize);
        }
    }

    private enum ELFIdentification {

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

        ELFIdentification(int value) {
            this.value = value;
        }

        int value() {
            return this.value;
        }
    }

    private enum EI_Class {
        ELFCLASSNONE(0),
        ELFCLASS32(1),
        ELFCLASS64(2);

        final String[] textual = {
            "None", "32", "64"
        };

        private final int value;

        EI_Class(int value) {
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

        int value() {
            return this.value;
        }

        @Override
        public String toString() {
            return textual[value];
        }
    }

    private enum EI_Data {
        ELFDATANONE(0),
        ELFDATA2LSB(1),
        ELFDATA2MSB(2);

        private final int value;

        EI_Data(int value) {
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

        int value() {
            return this.value;
        }
    }

    private enum E_Type {
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

        E_Type(int value) {
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

        int value() {
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

    private enum E_Machine {
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

        E_Machine(int value) {
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

        int value() {
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

    private enum E_Version {
        EV_NONE(0),
        EV_CURRENT(1);

        final String[] textual = {
            "Invalid", "Current"
        };

        private final int value;

        E_Version(int value) {
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

        int value() {
            return this.value;
        }

        @Override
        public String toString() {
            return textual[value];
        }
    }
}
