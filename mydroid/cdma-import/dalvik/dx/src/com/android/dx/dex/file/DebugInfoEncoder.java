/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dx.dex.file;

import com.android.dx.dex.code.LocalList;
import com.android.dx.dex.code.PositionList;
import com.android.dx.rop.code.RegisterSpec;
import com.android.dx.rop.code.SourcePosition;
import com.android.dx.rop.cst.CstMethodRef;
import com.android.dx.rop.cst.CstType;
import com.android.dx.rop.cst.CstUtf8;
import com.android.dx.rop.type.Prototype;
import com.android.dx.rop.type.StdTypeList;
import com.android.dx.rop.type.Type;
import com.android.dx.util.ByteArrayAnnotatedOutput;
import com.android.dx.util.AnnotatedOutput;
import com.android.dx.util.ExceptionWithContext;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.BitSet;

import static com.android.dx.dex.file.DebugInfoConstants.*;

/**
 * An encoder for the dex debug info state machine format. The format
 * for each method enrty is as follows:
 * <ol>
 * <li> signed LEB128: initial value for line register.
 * <li> n instances of signed LEB128: string indicies (offset by 1)
 * for each method argument in left-to-right order
 * with <code>this</code> excluded. A value of '0' indicates "no name"
 * <li> A sequence of special or normal opcodes as defined in
 * <code>DebugInfoConstants</code>.
 * <li> A single terminating <code>OP_END_SEQUENCE</code>
 * </ol>
 */
public final class DebugInfoEncoder {
    private static final boolean DEBUG = false;

    private final PositionList positionlist;
    private final LocalList locallist;
    private final ByteArrayAnnotatedOutput output;
    private final DexFile file;
    private final int codeSize;
    private final int regSize;

    private final Prototype desc;
    private final boolean isStatic;

    /** current encoding state: bytecode address */
    private int address = 0;

    /** current encoding state: line number */
    private int line = 1;

    /**
     * if non-null: the output to write annotations to. No normal
     * output is written to this.
     */
    private AnnotatedOutput annotateTo;

    /** if non-null: another possible output for annotations */
    private PrintWriter debugPrint;

    /** if non-null: the prefix for each annotation or debugPrint line */
    private String prefix;

    /** true if output should be consumed during annotation */
    private boolean shouldConsume;

    /** indexed by register; last local alive in register */
    private final LocalList.Entry[] lastEntryForReg;

    /**
     * Creates an instance.
     *
     * @param pl null-ok
     * @param ll null-ok
     * @param file null-ok; may only be <code>null</code> if simply using
     * this class to do a debug print
     * @param codeSize
     * @param regSize
     * @param isStatic
     * @param ref
     */
    public DebugInfoEncoder(PositionList pl, LocalList ll,
            DexFile file, int codeSize, int regSize,
            boolean isStatic, CstMethodRef ref) {

        this.positionlist = pl;
        this.locallist = ll;
        this.file = file;
        output = new ByteArrayAnnotatedOutput();
        this.desc = ref.getPrototype();
        this.isStatic = isStatic;

        this.codeSize = codeSize;
        this.regSize = regSize;

        lastEntryForReg = new LocalList.Entry[regSize];
    }

    /**
     * Annotates or writes a message to the <code>debugPrint</code> writer
     * if applicable.
     *
     * @param length the number of bytes associated with this message
     * @param message the message itself
     */
    private void annotate(int length, String message) {
        if (prefix != null) {
            message = prefix + message;
        }

        if (annotateTo != null) {
            annotateTo.annotate(shouldConsume ? length : 0, message);
        }

        if (debugPrint != null) {
            debugPrint.println(message);
        }
    }

    /**
     * Converts this (PositionList, LocalList) pair into a state machine
     * sequence.
     *
     * @return encoded byte sequence without padding and
     * terminated with a <code>'\00'</code>
     */
    public byte[] convert() {
        try {
            byte[] ret;
            ret = convert0();

            if (DEBUG) {
                for (int i = 0 ; i < ret.length; i++) {
                    System.err.printf("byte %02x\n", (0xff & ret[i]));
                }
            }

            return ret;
        } catch (IOException ex) {
            throw ExceptionWithContext
                    .withContext(ex, "...while encoding debug info");
        }
    }

    /**
     * Converts and produces annotations on a stream. Does not write
     * actual bits to the <code>AnnotatedOutput</code>.
     *
     * @param prefix null-ok; prefix to attach to each line of output
     * @param debugPrint null-ok; if specified, an alternate output for
     * annotations
     * @param out null-ok; if specified, where annotations should go
     * @param consume whether to claim to have consumed output for
     * <code>out</code>
     * @return output sequence
     */
    public byte[] convertAndAnnotate(String prefix, PrintWriter debugPrint,
            AnnotatedOutput out, boolean consume) {
        this.prefix = prefix;
        this.debugPrint = debugPrint;
        annotateTo = out;
        shouldConsume = consume;

        byte[] result = convert();

        return result;
    }
    
    private byte[] convert0() throws IOException {
        ArrayList<PositionList.Entry> sortedPositions = buildSortedPositions();
        ArrayList<LocalList.Entry> sortedLocalsStart = buildLocalsStart();

        // Parameter locals are removed from sortedLocalsStart here.
        ArrayList<LocalList.Entry> methodArgs
                = extractMethodArguments(sortedLocalsStart);

        ArrayList<LocalList.Entry> sortedLocalsEnd
                = buildLocalsEnd(sortedLocalsStart);

        emitHeader(sortedPositions, methodArgs);

        // TODO: Make this mark the actual prologue end.
        output.writeByte(DBG_SET_PROLOGUE_END);

        if (annotateTo != null || debugPrint != null) {
            annotate(1, String.format("%04x: prologue end",address));
        }

        int szp = sortedPositions.size();
        int szl = sortedLocalsStart.size();

        // Current index in sortedPositions
        int curp = 0;
        // Current index in sortedLocalsStart
        int curls = 0;
        // Current index in sortedLocalsEnd
        int curle = 0;

        for (;;) {
            /*
             * Emit any information for the current address.
             */

            curle = emitLocalEndsAtAddress(curle, sortedLocalsEnd, curls,
                    sortedLocalsStart);

            /*
             * Our locals-sorted-by-range-end has reached the end
             * of the code block. Ignore everything else.
             */
            if (address == codeSize) {
                curle = szl;
            }

            curls = emitLocalStartsAtAddress(curls, sortedLocalsStart);

            curp = emitPositionsAtAddress(curp, sortedPositions);

            /*
             * Figure out what the next important address is.
             */

            int nextAddrLS = Integer.MAX_VALUE;
            int nextAddrLE = Integer.MAX_VALUE;
            int nextAddrP = Integer.MAX_VALUE;

            if (curls < szl) {
                nextAddrLS = sortedLocalsStart.get(curls).getStart();
            }

            if (curle < szl) {
                nextAddrLE = sortedLocalsEnd.get(curle).getEnd();
            }

            if (curp < szp) {
                nextAddrP = sortedPositions.get(curp).getAddress();
            }

            int next = Math.min(nextAddrP, Math.min(nextAddrLS, nextAddrLE));

            // No next important address == done.
            if (next == Integer.MAX_VALUE) {
                break;
            }

            /*
             * If the only work remaining are local ends at the end of the
             * block, stop here. Those are implied anyway.
             */
            if (next == codeSize
                    && nextAddrLS == Integer.MAX_VALUE
                    && nextAddrP == Integer.MAX_VALUE) {
                break;                
            }

            if (next == nextAddrP) {
                // Combined advance PC + position entry
                emitPosition(sortedPositions.get(curp++));
            } else {
                emitAdvancePc(next - address);
            }
        }

        emitEndSequence();

        byte[] result = output.toByteArray();
        
        if (DEBUG) {
            int origSize = 0;
            int newSize = result.length;

            if (positionlist != null) {
                origSize +=  (positionlist.size() * 6);
            }

            if (locallist != null) {
                origSize += (4 * 5 * locallist.size());
            }

            System.err.printf(
                    "Lines+Locals table was %d bytes is now %d bytes\n",
                    origSize, newSize);
        }
        
        return result;
    }

    /**
     * Emits all local ends that occur at the current <code>address</code>
     *
     * @param curle Current index in sortedLocalsEnd
     * @param sortedLocalsEnd Locals, sorted by ascending end address
     * @param curls Current index in sortedLocalsStart
     * @param sortedLocalsStart Locals, sorted by ascending start address
     * @return new value for <code>curle</code>
     * @throws IOException
     */
    private int emitLocalEndsAtAddress(int curle,
            ArrayList<LocalList.Entry> sortedLocalsEnd, int curls,
            ArrayList<LocalList.Entry> sortedLocalsStart)
            throws IOException {

        int szl = sortedLocalsEnd.size();

        // Ignore "local ends" at end of code.
        while (curle < szl
                && sortedLocalsEnd.get(curle).getEnd() == address
                && address != codeSize) {

            boolean skipLocalEnd = false;

            /*
             * Check to see if there's a range-start that appears at
             * the same address for the same register. If so, the
             * end-range is implicit so skip it.
             */
            for (int j = curls; j < szl
                    && sortedLocalsStart.get(j).getStart() == address
                    ; j++) {

                if (sortedLocalsStart.get(j).getRegister()
                        == sortedLocalsEnd.get(curle).getRegister()) {
                    skipLocalEnd = true;

                    if (DEBUG) {
                        System.err.printf("skip local end v%d\n",
                                sortedLocalsEnd.get(curle).getRegister());
                    }
                    curle++;
                    break;
                }
            }

            if (!skipLocalEnd) {
                emitLocalEnd(sortedLocalsEnd.get(curle++));
            }
        }
        return curle;
    }

    /**
     * Emits all local starts that occur at the current <code>address</code>
     *
     * @param curls Current index in sortedLocalsStart
     * @param sortedLocalsStart Locals, sorted by ascending start address
     * @return new value for <code>curls</code>
     * @throws IOException
     */
    private int emitLocalStartsAtAddress(int curls,
            ArrayList<LocalList.Entry> sortedLocalsStart)
            throws IOException {

        int szl = sortedLocalsStart.size();

        while (curls < szl
                && sortedLocalsStart.get(curls).getStart() == address) {
            LocalList.Entry lle = sortedLocalsStart.get(curls++);
            LocalList.Entry prevlle = lastEntryForReg[lle.getRegister()];

            if (lle == prevlle) {
                /*
                 * Here we ignore locals entries for parameters,
                 * which have already been represented and placed in the
                 * lastEntryForReg array.
                 */
                continue;
            } else if (prevlle != null && lle.matches(prevlle)) {
                if (prevlle.getEnd() == lle.getStart()) {
                    /*
                     * An adjacent range with the same register.
                     * The previous emitLocalEndsAtAddress() call skipped
                     * this local end, so we'll skip this local start as well.
                     */
                    continue;
                } else {
                    emitLocalRestart(lle);
                }
            } else {
                lastEntryForReg[lle.getRegister()] = lle;
                emitLocalStart(lle);
            }
        }
        return curls;
    }

    /**
     * Emits all positions that occur at the current <code>address</code>
     *
     * @param curp Current index in sortedPositions
     * @param sortedPositions positions, sorted by ascending address
     * @return new value for <code>curp</code>
     * @throws IOException
     */
    private int emitPositionsAtAddress(int curp,
            ArrayList<PositionList.Entry> sortedPositions)
            throws IOException {

        int szp = sortedPositions.size();
        while (curp < szp
                && sortedPositions.get(curp).getAddress() == address) {
            emitPosition(sortedPositions.get(curp++));
        }
        return curp;
    }

    /**
     * Emits the header sequence, which consists of LEB128-encoded initial
     * line number and string indicies for names of all non-"this" arguments.
     *  
     * @param sortedPositions positions, sorted by ascending address
     * @param methodArgs local list entries for method argumens arguments,
     * in left-to-right order omitting "this"
     * @throws IOException
     */
    private void emitHeader(ArrayList<PositionList.Entry> sortedPositions,
            ArrayList<LocalList.Entry> methodArgs) throws IOException {
        boolean annotate = (annotateTo != null) || (debugPrint != null);
        int mark = output.getCursor();

        // Start by initializing the line number register.
        if (sortedPositions.size() > 0) {
            PositionList.Entry entry = sortedPositions.get(0);
            line = entry.getPosition().getLine();
        }
        output.writeUnsignedLeb128(line);

        if (annotate) {
            annotate(output.getCursor() - mark, "line_start: " + line);
        }

        int curParam = getParamBase();
        // paramTypes will not include 'this'
        StdTypeList paramTypes = desc.getParameterTypes();
        int szParamTypes = paramTypes.size();

        /*
         * Initialize lastEntryForReg to have an initial
         * entry for the 'this' pointer.
         */
        if (!isStatic) {
            for (LocalList.Entry arg: methodArgs) {
                if (curParam == arg.getRegister()) {
                    lastEntryForReg[curParam] = arg;
                    break;
                }
            }
            curParam++;
        }

        // Write out the number of parameter entries that will follow.
        mark = output.getCursor();
        output.writeUnsignedLeb128(szParamTypes);

        if (annotate) {
            annotate(output.getCursor() - mark, 
                    String.format("parameters_size: %04x", szParamTypes));
        }

        /*
         * Then emit the string indicies of all the method parameters.
         * Note that 'this', if applicable, is excluded.
         */
        for (int i = 0; i < szParamTypes; i++) {
            Type pt = paramTypes.get(i);
            LocalList.Entry found = null;

            mark = output.getCursor();

            for (LocalList.Entry arg: methodArgs) {
                if (curParam == arg.getRegister()) {
                    found = arg;

                    if (arg.getSignature() != null) {
                        /*
                         * Parameters with signatures will be re-emitted
                         * in complete as LOCAL_START_EXTENDED's below.
                         */
                        emitStringIndex(null);
                    } else {
                        emitStringIndex(arg.getName());
                    }
                    lastEntryForReg[curParam] = arg;

                    break;
                }
            }

            if (found == null) {
                /*
                 * Emit a null symbol for "unnamed." This is common
                 * for, e.g., synthesized methods and inner-class
                 * this$0 arguments.
                 */
                emitStringIndex(null);
            }

            if (annotate) {
                String parameterName
                        = (found == null || found.getSignature() != null)
                                ? "<unnamed>" : found.getName().toHuman();
                annotate(output.getCursor() - mark,
                        "parameter " + parameterName + " "
                                + RegisterSpec.PREFIX + curParam);
            }

            curParam += pt.getCategory();
        }

        /*
         * If anything emitted above has a type signature, emit it again as
         * a LOCAL_RESTART_EXTENDED
         */

        for (LocalList.Entry arg: lastEntryForReg) {
            if (arg == null) {
                continue;
            }

            CstUtf8 signature = arg.getSignature();

            if (signature != null) {
                emitLocalStartExtended(arg);
            }
        }
    }

    /**
     * Builds a list of position entries, sorted by ascending address.
     *
     * @return A sorted positions list
     */
    private ArrayList<PositionList.Entry> buildSortedPositions() {
        int sz = (positionlist == null) ? 0 : positionlist.size();
        ArrayList<PositionList.Entry> result = new ArrayList(sz);

        for (int i = 0; i < sz; i++) {
            result.add(positionlist.get(i));
        }

        // Sort ascending by address.
        Collections.sort (result, new Comparator<PositionList.Entry>() {
            public int compare (PositionList.Entry a, PositionList.Entry b) {
                return a.getAddress() - b.getAddress();
            }

            public boolean equals (Object obj) {
               return obj == this;
            }
        });
        return result;
    }

    /**
     * Builds a list of locals entries sorted by ascending start address.
     *
     * @return A sorted locals list list
     */
    private ArrayList<LocalList.Entry> buildLocalsStart() {
        int sz = (locallist == null) ? 0 : locallist.size();
        ArrayList<LocalList.Entry> result = new ArrayList(sz);

        // Add all the entries
        for (int i = 0; i < sz; i++) {
            LocalList.Entry e = locallist.get(i);
            result.add(locallist.get(i));
        }

        // Sort ascending by start address.
        Collections.sort (result, new Comparator<LocalList.Entry>() {
            public int compare (LocalList.Entry a, LocalList.Entry b) {
                return a.getStart() - b.getStart();
            }

            public boolean equals (Object obj) {
               return obj == this;
            }
        });
        return result;
    }

    /**
     * Builds a list of locals entries sorted by ascending end address.
     * 
     * @param list locals list in any order
     * @return a sorted locals list
     */
    private ArrayList<LocalList.Entry> buildLocalsEnd(
            ArrayList<LocalList.Entry> list) {

        ArrayList<LocalList.Entry> sortedLocalsEnd  = new ArrayList(list);

        // Sort ascending by end address.
        Collections.sort (sortedLocalsEnd, new Comparator<LocalList.Entry>() {
            public int compare (LocalList.Entry a, LocalList.Entry b) {
                return a.getEnd() - b.getEnd();
            }

            public boolean equals (Object obj) {
               return obj == this;
            }
        });
        return sortedLocalsEnd;
    }

    /**
     * Gets the register that begins the method's parameter range (including
     * the 'this' parameter for non-static methods). The range continues until
     * <code>regSize</code>
     *
     * @return register as noted above
     */
    private int getParamBase() {
        return regSize
                - desc.getParameterTypes().getWordCount() - (isStatic? 0 : 1);
    }

    /**
     * Extracts method arguments from a locals list. These will be collected
     * from the input list and sorted by ascending register in the
     * returned list.
     *
     * @param sortedLocals locals list, sorted by ascending start address,
     * to process; left unmodified
     * @return list of non-<code>this</code> method argument locals,
     * sorted by ascending register
     */
    private ArrayList<LocalList.Entry> extractMethodArguments (
            ArrayList<LocalList.Entry> sortedLocals) {

        ArrayList<LocalList.Entry> result
                = new ArrayList(desc.getParameterTypes().size());

        int argBase = getParamBase();

        BitSet seen = new BitSet(regSize - argBase);

        int sz = sortedLocals.size();
        for (int i = 0; i < sz; i++) {
            LocalList.Entry e = sortedLocals.get(i);
            int reg = e.getRegister();

            if (reg < argBase) {
                continue;
            }

            // only the lowest-start-address entry is included.
            if (seen.get(reg - argBase)) {
                continue;
            }

            seen.set(reg - argBase);
            result.add(e);
        }

        // Sort by ascending register.
        Collections.sort (result, new Comparator<LocalList.Entry>() {
            public int compare (LocalList.Entry a, LocalList.Entry b) {
                return a.getRegister() - b.getRegister();
            }

            public boolean equals (Object obj) {
               return obj == this;
            }
        });

        return result;
    }

    /**
     * Returns a string representation of this LocalList entry that is
     * appropriate for emitting as an annotation.
     *
     * @param e non-null; entry
     * @return non-null; annotation string
     */
    private String entryAnnotationString(LocalList.Entry e) {
        StringBuilder sb = new StringBuilder();

        sb.append(RegisterSpec.PREFIX);
        sb.append(e.getRegister());
        sb.append(' ');

        CstUtf8 name = e.getName();
        if (name == null) {
            sb.append("null");
        } else {
            sb.append(name.toHuman());
        }
        sb.append(' ');

        CstType type = e.getType();
        if (type == null) {
            sb.append("null");
        } else {
            sb.append(type.toHuman());
        }

        CstUtf8 signature = e.getSignature();

        if (signature != null) {
            sb.append(' ');
            sb.append(signature.toHuman());
        }

        return sb.toString();
    }

    /**
     * Emits a {@link DebugInfoConstants#DBG_RESTART_LOCAL DBG_RESTART_LOCAL}
     * sequence.
     *
     * @param entry entry associated with this restart
     * @throws IOException
     */
    private void emitLocalRestart(LocalList.Entry entry)
            throws IOException {

        int mark = output.getCursor();

        output.writeByte(DBG_RESTART_LOCAL);
        emitUnsignedLeb128(entry.getRegister());

        if (annotateTo != null || debugPrint != null) {
            annotate(output.getCursor() - mark,
                    String.format("%04x: +local restart %s",
                            address, entryAnnotationString(entry)));
        }

        if (DEBUG) {
            System.err.println("emit local restart");
        }
    }

    /**
     * Emits a string index as an unsigned LEB128. The actual value written
     * is shifted by 1, so that the '0' value is reserved for "null". The
     * null symbol is used in some cases by the parameter name list
     * at the beginning of the sequence.
     *
     * @param string null-ok; string to emit
     * @throws IOException
     */
    private void emitStringIndex(CstUtf8 string) throws IOException {
        if ((string == null) || (file == null)) {
            output.writeUnsignedLeb128(0);
        } else {
            output.writeUnsignedLeb128(
                1 + file.getStringIds().indexOf(string));
        }

        if (DEBUG) {
            System.err.printf("Emit string %s\n",
                    string == null ? "<null>" : string.toQuoted());
        }
    }

    /**
     * Emits a type index as an unsigned LEB128. The actual value written
     * is shifted by 1, so that the '0' value is reserved for "null".
     *
     * @param type null-ok; type to emit
     * @throws IOException
     */
    private void emitTypeIndex(CstType type) throws IOException {
        if ((type == null) || (file == null)) {
            output.writeUnsignedLeb128(0);
        } else {
            output.writeUnsignedLeb128(
                1 + file.getTypeIds().indexOf(type));
        }

        if (DEBUG) {
            System.err.printf("Emit type %s\n",
                    type == null ? "<null>" : type.toHuman());
        }
    }

    /**
     * Emits a {@link DebugInfoConstants#DBG_START_LOCAL DBG_START_LOCAL} or
     * {@link DebugInfoConstants#DBG_START_LOCAL_EXTENDED
     * DBG_START_LOCAL_EXTENDED} sequence.
     *
     * @param entry entry to emit
     * @throws IOException
     */
    private void emitLocalStart(LocalList.Entry entry)
        throws IOException {

        if (entry.getSignature() != null) {
            emitLocalStartExtended(entry);
            return;
        }

        int mark = output.getCursor();

        output.writeByte(DBG_START_LOCAL);

        emitUnsignedLeb128 (entry.getRegister());
        emitStringIndex(entry.getName());
        emitTypeIndex(entry.getType());

        if (annotateTo != null || debugPrint != null) {
            annotate(output.getCursor() - mark,
                    String.format("%04x: +local %s", address,
                            entryAnnotationString(entry)));
        }

        if (DEBUG) {
            System.err.println("emit local start");
        }
    }

    /**
     * Emits a {@link DebugInfoConstants#DBG_START_LOCAL_EXTENDED
     * DBG_START_LOCAL_EXTENDED} sequence.
     *
     * @param entry entry to emit
     * @throws IOException
     */
    private void emitLocalStartExtended(LocalList.Entry entry)
        throws IOException {

        int mark = output.getCursor();

        output.writeByte(DBG_START_LOCAL_EXTENDED);

        emitUnsignedLeb128 (entry.getRegister());
        emitStringIndex(entry.getName());
        emitTypeIndex(entry.getType());
        emitStringIndex(entry.getSignature());

        if (annotateTo != null || debugPrint != null) {
            annotate(output.getCursor() - mark,
                    String.format("%04x: +localx %s", address,
                            entryAnnotationString(entry)));
        }

        if (DEBUG) {
            System.err.println("emit local start");
        }
    }

    /**
     * Emits a {@link DebugInfoConstants#DBG_END_LOCAL DBG_END_LOCAL} sequence.
     *
     * @param entry entry non-null; entry associated with end.
     * @throws IOException
     */
    private void emitLocalEnd(LocalList.Entry entry)
            throws IOException {

        int mark = output.getCursor();

        output.writeByte(DBG_END_LOCAL);
        output.writeUnsignedLeb128(entry.getRegister());

        if (annotateTo != null || debugPrint != null) {
            annotate(output.getCursor() - mark,
                    String.format("%04x: -local %s", address,
                            entryAnnotationString(entry)));
        }

        if (DEBUG) {
            System.err.println("emit local end");
        }
    }

    /**
     * Emits the necessary byte sequences to emit the given position table
     * entry. This will typically be a single special opcode, although
     * it may also require DBG_ADVANCE_PC or DBG_ADVANCE_LINE.
     *
     * @param entry position entry to emit.
     * @throws IOException
     */
    private void emitPosition(PositionList.Entry entry)
            throws IOException {

        SourcePosition pos = entry.getPosition();
        int newLine = pos.getLine();
        int newAddress = entry.getAddress();

        int opcode;

        int deltaLines = newLine - line;
        int deltaAddress = newAddress - address;

        if (deltaAddress < 0) {
            throw new RuntimeException(
                    "Position entries must be in ascending address order");
        }

        if ((deltaLines < DBG_LINE_BASE)
                || (deltaLines > (DBG_LINE_BASE + DBG_LINE_RANGE -1))) {
            emitAdvanceLine(deltaLines);
            deltaLines = 0;
        }

        opcode = computeOpcode (deltaLines, deltaAddress);

        if ((opcode & ~0xff) > 0) {
            emitAdvancePc(deltaAddress);
            deltaAddress = 0;
            opcode = computeOpcode (deltaLines, deltaAddress);

            if ((opcode & ~0xff) > 0) {
                emitAdvanceLine(deltaLines);
                deltaLines = 0;
                opcode = computeOpcode (deltaLines, deltaAddress);
            }
        }

        output.writeByte(opcode);

        line += deltaLines;
        address += deltaAddress;

        if (annotateTo != null || debugPrint != null) {
            annotate(1,
                    String.format ("%04x: line %d", address, line));
        }
    }

    /**
     * Computes a special opcode that will encode the given position change.
     * If the return value is > 0xff, then the request cannot be fulfilled.
     * Essentially the same as described in "DWARF Debugging Format Version 3"
     * section 6.2.5.1.
     *
     * @param deltaLines &gt;= DBG_LINE_BASE and &lt;= DBG_LINE_BASE +
     * DBG_LINE_RANGE, the line change to encode
     * @param deltaAddress &gt;= 0; the address change to encode
     * @return &lt;= 0xff if in range, otherwise parameters are out of range
     */
    private static int computeOpcode(int deltaLines, int deltaAddress) {
        if (deltaLines < DBG_LINE_BASE
                || deltaLines > (DBG_LINE_BASE + DBG_LINE_RANGE -1)) {

            throw new RuntimeException ("Parameter out of range");            
        }

        return  (deltaLines - DBG_LINE_BASE)
            + (DBG_LINE_RANGE * deltaAddress) + DBG_FIRST_SPECIAL;
    }

    /**
     * Emits an {@link DebugInfoConstants#DBG_ADVANCE_LINE DBG_ADVANCE_LINE}
     * sequence.
     *
     * @param deltaLines amount to change line number register by
     * @throws IOException
     */
    private void emitAdvanceLine(int deltaLines) throws IOException {
        int mark = output.getCursor();

        output.writeByte(DBG_ADVANCE_LINE);
        output.writeSignedLeb128(deltaLines);
        line += deltaLines;

        if (annotateTo != null || debugPrint != null) {
            annotate(output.getCursor() - mark,
                    String.format("line = %d", line));
        }

        if (DEBUG) {
            System.err.printf("Emitting advance_line for %d\n", deltaLines);
        }
    }

    /**
     * Emits an  {@link DebugInfoConstants#DBG_ADVANCE_PC DBG_ADVANCE_PC} 
     * sequence.
     *
     * @param deltaAddress &gt;= 0 amount to change program counter by
     * @throws IOException
     */
    private void emitAdvancePc(int deltaAddress) throws IOException {
        int mark = output.getCursor();

        output.writeByte(DBG_ADVANCE_PC);
        output.writeUnsignedLeb128(deltaAddress);
        address += deltaAddress;

        if (annotateTo != null || debugPrint != null) {
            annotate(output.getCursor() - mark,
                    String.format("%04x: advance pc", address));
        }

        if (DEBUG) {
            System.err.printf("Emitting advance_pc for %d\n", deltaAddress);
        }
    }

    /**
     * Emits an unsigned LEB128 value.
     *
     * @param n &gt= 0 vallue to emit. Note that, although this can represent
     * integers larger than Integer.MAX_VALUE, we currently don't allow that.
     * @throws IOException
     */
    private void emitUnsignedLeb128(int n) throws IOException {
        // We'll never need the top end of the unsigned range anyway.
        if (n < 0) {
            throw new RuntimeException(
                    "Signed value where unsigned required: " + n);
        }

        output.writeSignedLeb128(n);
    }

    /**
     * Emits the {@link DebugInfoConstants#DBG_END_SEQUENCE DBG_END_SEQUENCE}
     * bytecode.
     */
    private void emitEndSequence() {
        output.writeByte(DBG_END_SEQUENCE);

        if (annotateTo != null || debugPrint != null) {
            annotate(1, "end sequence");
        }
    }
}
