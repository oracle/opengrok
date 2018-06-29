//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.opengrok.suggest.query.customized;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.ConjunctionDISI;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.Similarity.SimScorer;
import org.apache.lucene.util.FixedBitSet;
import org.opengrok.suggest.query.PhraseScorer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class SloppyPhraseScorer extends Scorer implements PhraseScorer {

    public int suggestOffset = 0;

    private Map<Integer, Set<Integer>> map = new HashMap<>();

    private final DocIdSetIterator conjunction;
    private final PhrasePositions[] phrasePositions;
    private float sloppyFreq;
    private final SimScorer docScorer;
    private final int slop;
    private final int numPostings;
    private final PhraseQueue pq;
    private int end;
    private boolean hasRpts;
    private boolean checkedRpts;
    private boolean hasMultiTermRpts;
    private PhrasePositions[][] rptGroups;
    private PhrasePositions[] rptStack;
    private int numMatches;
    final boolean needsScores;
    private final float matchCost;

    SloppyPhraseScorer(Weight weight, MyPhraseQuery.PostingsAndFreq[] postings, int slop, SimScorer docScorer, boolean needsScores, float matchCost) {
        super(weight);
        this.docScorer = docScorer;
        this.needsScores = needsScores;
        this.slop = slop;
        this.numPostings = postings == null ? 0 : postings.length;
        this.pq = new PhraseQueue(postings.length);
        DocIdSetIterator[] iterators = new DocIdSetIterator[postings.length];
        this.phrasePositions = new PhrasePositions[postings.length];

        for(int i = 0; i < postings.length; ++i) {
            iterators[i] = postings[i].postings;
            this.phrasePositions[i] = new PhrasePositions(postings[i].postings, postings[i].position, i, postings[i].terms);
        }

        if (iterators.length > 1) {
            this.conjunction = ConjunctionDISI.intersectIterators(Arrays.asList(iterators));
        } else {
            this.conjunction = iterators[0];
        }

        assert TwoPhaseIterator.unwrap(this.conjunction) == null;

        this.matchCost = matchCost;
    }

    private float phraseFreq() throws IOException {
        Set<Integer> allPositions = new HashSet<>();

        Set<Integer> positions = new HashSet<>();
        map.put(docID(), positions);

        if (!this.initPhrasePositions()) {
            return 0.0F;
        } else {

            for (PhrasePositions phrasePositions : this.pq) {
                allPositions.add(phrasePositions.position + phrasePositions.offset);
            }

            int biggestOffset = 0;
            for (PhrasePositions phrasePositions : this.pq) {
                if (phrasePositions.offset > biggestOffset) {
                    biggestOffset = phrasePositions.offset;
                }
            }

            float freq = 0.0F;
            this.numMatches = 0;
            PhrasePositions pp = (PhrasePositions)this.pq.pop();
            int matchLength = this.end - pp.position;
            int next = ((PhrasePositions)this.pq.top()).position;

            int lastEnd = this.end;

            while(this.advancePP(pp) && (!this.hasRpts || this.advanceRpts(pp))) {

                allPositions.add(pp.position + pp.offset);


                if (pp.position > next) {
                    if (matchLength <= this.slop) {
                        freq += this.docScorer.computeSlopFactor(matchLength);
                        ++this.numMatches;

                        int expectedPos = lastEnd + suggestOffset;

                        int range = this.slop - matchLength;
                        for (int i = 0; i < (2 * range) + 1; i++) {
                            int pos = expectedPos + i - range;
                            if (pos > 0 && !allPositions.contains(pos)) {
                                positions.add(pos);
                            }
                        }


                        //if (!this.needsScores) {
                        //    return freq;
                        //}
                    }

                    this.pq.add(pp);
                    pp = (PhrasePositions)this.pq.pop();
                    next = ((PhrasePositions)this.pq.top()).position;
                    matchLength = this.end - pp.position;
                    lastEnd = this.end; //endOffset; //this.end;
                } else {
                    int matchLength2 = this.end - pp.position;
                    if (matchLength2 < matchLength) {
                        matchLength = matchLength2;
                        lastEnd = this.end;
                    }
                }
            }

            if (matchLength <= this.slop) {
                freq += this.docScorer.computeSlopFactor(matchLength);
                ++this.numMatches;


                int expectedPos = lastEnd + suggestOffset;

                int range = this.slop - matchLength;
                for (int i = 0; i < (2 * range) + 1; i++) {
                    int pos = expectedPos + i - range;
                    if (pos > 0 && !allPositions.contains(pos)) {
                        positions.add(pos);
                    }
                }


            }

            return freq;
        }
    }

    private boolean advancePP(PhrasePositions pp) throws IOException {
        if (!pp.nextPosition()) {
            return false;
        } else {
            if (pp.position > this.end) {
                this.end = pp.position;
            }

            return true;
        }
    }

    private boolean advanceRpts(PhrasePositions pp) throws IOException {
        if (pp.rptGroup < 0) {
            return true;
        } else {
            PhrasePositions[] rg = this.rptGroups[pp.rptGroup];
            FixedBitSet bits = new FixedBitSet(rg.length);
            int k0 = pp.rptInd;

            int k;
            while((k = this.collide(pp)) >= 0) {
                pp = this.lesser(pp, rg[k]);
                if (!this.advancePP(pp)) {
                    return false;
                }

                if (k != k0) {
                    bits = FixedBitSet.ensureCapacity(bits, k);
                    bits.set(k);
                }
            }

            int n = 0;
            int numBits = bits.length();

            while(bits.cardinality() > 0) {
                PhrasePositions pp2 = (PhrasePositions)this.pq.pop();
                this.rptStack[n++] = pp2;
                if (pp2.rptGroup >= 0 && pp2.rptInd < numBits && bits.get(pp2.rptInd)) {
                    bits.clear(pp2.rptInd);
                }
            }

            for(int i = n - 1; i >= 0; --i) {
                this.pq.add(this.rptStack[i]);
            }

            return true;
        }
    }

    private PhrasePositions lesser(PhrasePositions pp, PhrasePositions pp2) {
        return pp.position >= pp2.position && (pp.position != pp2.position || pp.offset >= pp2.offset) ? pp2 : pp;
    }

    private int collide(PhrasePositions pp) {
        int tpPos = this.tpPos(pp);
        PhrasePositions[] rg = this.rptGroups[pp.rptGroup];

        for(int i = 0; i < rg.length; ++i) {
            PhrasePositions pp2 = rg[i];
            if (pp2 != pp && this.tpPos(pp2) == tpPos) {
                return pp2.rptInd;
            }
        }

        return -1;
    }

    private boolean initPhrasePositions() throws IOException {
        this.end = -2147483648;
        if (!this.checkedRpts) {
            return this.initFirstTime();
        } else if (!this.hasRpts) {
            this.initSimple();
            return true;
        } else {
            return this.initComplex();
        }
    }

    private void initSimple() throws IOException {
        this.pq.clear();
        PhrasePositions[] var1 = this.phrasePositions;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            PhrasePositions pp = var1[var3];
            pp.firstPosition();
            if (pp.position > this.end) {
                this.end = pp.position;
            }

            this.pq.add(pp);
        }

    }

    private boolean initComplex() throws IOException {
        this.placeFirstPositions();
        if (!this.advanceRepeatGroups()) {
            return false;
        } else {
            this.fillQueue();
            return true;
        }
    }

    private void placeFirstPositions() throws IOException {
        PhrasePositions[] var1 = this.phrasePositions;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            PhrasePositions pp = var1[var3];
            pp.firstPosition();
        }

    }

    private void fillQueue() {
        this.pq.clear();
        PhrasePositions[] var1 = this.phrasePositions;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            PhrasePositions pp = var1[var3];
            if (pp.position > this.end) {
                this.end = pp.position;
            }

            this.pq.add(pp);
        }

    }

    private boolean advanceRepeatGroups() throws IOException {
        PhrasePositions[][] var1 = this.rptGroups;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            PhrasePositions[] rg = var1[var3];
            int i;
            byte incr;
            if (this.hasMultiTermRpts) {
                for(i = 0; i < rg.length; i += incr) {
                    incr = 1;
                    PhrasePositions pp = rg[i];

                    int k;
                    while((k = this.collide(pp)) >= 0) {
                        PhrasePositions pp2 = this.lesser(pp, rg[k]);
                        if (!this.advancePP(pp2)) {
                            return false;
                        }

                        if (pp2.rptInd < i) {
                            incr = 0;
                            break;
                        }
                    }
                }
            } else {
                for(int j = 1; j < rg.length; ++j) {
                    for(i = 0; i < j; ++i) {
                        if (!rg[j].nextPosition()) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private boolean initFirstTime() throws IOException {
        this.checkedRpts = true;
        this.placeFirstPositions();
        LinkedHashMap<Term, Integer> rptTerms = this.repeatingTerms();
        this.hasRpts = !rptTerms.isEmpty();
        if (this.hasRpts) {
            this.rptStack = new PhrasePositions[this.numPostings];
            ArrayList<ArrayList<PhrasePositions>> rgs = this.gatherRptGroups(rptTerms);
            this.sortRptGroups(rgs);
            if (!this.advanceRepeatGroups()) {
                return false;
            }
        }

        this.fillQueue();
        return true;
    }

    private void sortRptGroups(ArrayList<ArrayList<PhrasePositions>> rgs) {
        this.rptGroups = new PhrasePositions[rgs.size()][];
        Comparator<PhrasePositions> cmprtr = new Comparator<PhrasePositions>() {
            public int compare(PhrasePositions pp1, PhrasePositions pp2) {
                return pp1.offset - pp2.offset;
            }
        };

        for(int i = 0; i < this.rptGroups.length; ++i) {
            PhrasePositions[] rg = (PhrasePositions[])((ArrayList)rgs.get(i)).toArray(new PhrasePositions[0]);
            Arrays.sort(rg, cmprtr);
            this.rptGroups[i] = rg;

            for(int j = 0; j < rg.length; rg[j].rptInd = j++) {
                ;
            }
        }

    }

    private ArrayList<ArrayList<PhrasePositions>> gatherRptGroups(LinkedHashMap<Term, Integer> rptTerms) throws IOException {
        PhrasePositions[] rpp = this.repeatingPPs(rptTerms);
        ArrayList<ArrayList<PhrasePositions>> res = new ArrayList();
        int g;
        if (!this.hasMultiTermRpts) {
            for(int i = 0; i < rpp.length; ++i) {
                PhrasePositions pp = rpp[i];
                if (pp.rptGroup < 0) {
                    int tpPos = this.tpPos(pp);

                    for(int j = i + 1; j < rpp.length; ++j) {
                        PhrasePositions pp2 = rpp[j];
                        if (pp2.rptGroup < 0 && pp2.offset != pp.offset && this.tpPos(pp2) == tpPos) {
                            g = pp.rptGroup;
                            if (g < 0) {
                                g = res.size();
                                pp.rptGroup = g;
                                ArrayList<PhrasePositions> rl = new ArrayList(2);
                                rl.add(pp);
                                res.add(rl);
                            }

                            pp2.rptGroup = g;
                            ((ArrayList)res.get(g)).add(pp2);
                        }
                    }
                }
            }
        } else {
            ArrayList<HashSet<PhrasePositions>> tmp = new ArrayList();
            ArrayList<FixedBitSet> bb = this.ppTermsBitSets(rpp, rptTerms);
            this.unionTermGroups(bb);
            HashMap<Term, Integer> tg = this.termGroups(rptTerms, bb);
            HashSet<Integer> distinctGroupIDs = new HashSet(tg.values());

            for(int i = 0; i < distinctGroupIDs.size(); ++i) {
                tmp.add(new HashSet());
            }

            PhrasePositions[] var22 = rpp;
            g = rpp.length;

            for(int var24 = 0; var24 < g; ++var24) {
                PhrasePositions pp = var22[var24];
                Term[] var12 = pp.terms;
                int var13 = var12.length;

                for(int var14 = 0; var14 < var13; ++var14) {
                    Term t = var12[var14];
                    if (rptTerms.containsKey(t)) {
                        int group = (Integer)tg.get(t);
                        ((HashSet)tmp.get(group)).add(pp);

                        assert pp.rptGroup == -1 || pp.rptGroup == group;

                        pp.rptGroup = group;
                    }
                }
            }

            Iterator var23 = tmp.iterator();

            while(var23.hasNext()) {
                HashSet<PhrasePositions> hs = (HashSet)var23.next();
                res.add(new ArrayList(hs));
            }
        }

        return res;
    }

    private final int tpPos(PhrasePositions pp) {
        return pp.position + pp.offset;
    }

    private LinkedHashMap<Term, Integer> repeatingTerms() {
        LinkedHashMap<Term, Integer> tord = new LinkedHashMap();
        HashMap<Term, Integer> tcnt = new HashMap();
        PhrasePositions[] var3 = this.phrasePositions;
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            PhrasePositions pp = var3[var5];
            Term[] var7 = pp.terms;
            int var8 = var7.length;

            for(int var9 = 0; var9 < var8; ++var9) {
                Term t = var7[var9];
                Integer cnt0 = (Integer)tcnt.get(t);
                Integer cnt = cnt0 == null ? new Integer(1) : new Integer(1 + cnt0);
                tcnt.put(t, cnt);
                if (cnt == 2) {
                    tord.put(t, tord.size());
                }
            }
        }

        return tord;
    }

    private PhrasePositions[] repeatingPPs(HashMap<Term, Integer> rptTerms) {
        ArrayList<PhrasePositions> rp = new ArrayList();
        PhrasePositions[] var3 = this.phrasePositions;
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            PhrasePositions pp = var3[var5];
            Term[] var7 = pp.terms;
            int var8 = var7.length;

            for(int var9 = 0; var9 < var8; ++var9) {
                Term t = var7[var9];
                if (rptTerms.containsKey(t)) {
                    rp.add(pp);
                    this.hasMultiTermRpts |= pp.terms.length > 1;
                    break;
                }
            }
        }

        return (PhrasePositions[])rp.toArray(new PhrasePositions[0]);
    }

    private ArrayList<FixedBitSet> ppTermsBitSets(PhrasePositions[] rpp, HashMap<Term, Integer> tord) {
        ArrayList<FixedBitSet> bb = new ArrayList(rpp.length);
        PhrasePositions[] var4 = rpp;
        int var5 = rpp.length;

        for(int var6 = 0; var6 < var5; ++var6) {
            PhrasePositions pp = var4[var6];
            FixedBitSet b = new FixedBitSet(tord.size());
            Term[] var10 = pp.terms;
            int var11 = var10.length;

            for(int var12 = 0; var12 < var11; ++var12) {
                Term t = var10[var12];
                Integer ord;
                if ((ord = (Integer)tord.get(t)) != null) {
                    b.set(ord);
                }
            }

            bb.add(b);
        }

        return bb;
    }

    private void unionTermGroups(ArrayList<FixedBitSet> bb) {
        byte incr;
        for(int i = 0; i < bb.size() - 1; i += incr) {
            incr = 1;
            int j = i + 1;

            while(j < bb.size()) {
                if (((FixedBitSet)bb.get(i)).intersects((FixedBitSet)bb.get(j))) {
                    ((FixedBitSet)bb.get(i)).or((FixedBitSet)bb.get(j));
                    bb.remove(j);
                    incr = 0;
                } else {
                    ++j;
                }
            }
        }

    }

    private HashMap<Term, Integer> termGroups(LinkedHashMap<Term, Integer> tord, ArrayList<FixedBitSet> bb) throws IOException {
        HashMap<Term, Integer> tg = new HashMap();
        Term[] t = (Term[])tord.keySet().toArray(new Term[0]);

        for(int i = 0; i < bb.size(); ++i) {
            FixedBitSet bits = (FixedBitSet)bb.get(i);

            for(int ord = bits.nextSetBit(0); ord != 2147483647; ord = ord + 1 >= bits.length() ? 2147483647 : bits.nextSetBit(ord + 1)) {
                tg.put(t[ord], i);
            }
        }

        return tg;
    }

    int freq() {
        return this.numMatches;
    }

    float sloppyFreq() {
        return this.sloppyFreq;
    }

    public int docID() {
        return this.conjunction.docID();
    }

    public float score() throws IOException {
        return this.docScorer.score(this.docID(), this.sloppyFreq);
    }

    public String toString() {
        return "scorer(" + this.weight + ")";
    }

    public TwoPhaseIterator twoPhaseIterator() {
        return new TwoPhaseIterator(this.conjunction) {
            public boolean matches() throws IOException {
                SloppyPhraseScorer.this.sloppyFreq = SloppyPhraseScorer.this.phraseFreq();
                return SloppyPhraseScorer.this.sloppyFreq != 0.0F;
            }

            public float matchCost() {
                return SloppyPhraseScorer.this.matchCost;
            }

            public String toString() {
                return "SloppyPhraseScorer@asTwoPhaseIterator(" + SloppyPhraseScorer.this + ")";
            }
        };
    }

    public DocIdSetIterator iterator() {
        return TwoPhaseIterator.asDocIdSetIterator(this.twoPhaseIterator());
    }

    @Override
    public Map<Integer, Set<Integer>> getMap() {
        return map;
    }
}
