//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.opengrok.suggest.query.customized;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;

import java.io.IOException;

final class PhrasePositions {

    int position;
    int count;
    int offset;
    final int ord;
    final PostingsEnum postings;
    PhrasePositions next;
    int rptGroup = -1;
    int rptInd;
    final Term[] terms;

    PhrasePositions(PostingsEnum postings, int o, int ord, Term[] terms) {
        this.postings = postings;
        this.offset = o;
        this.ord = ord;
        this.terms = terms;
    }

    final void firstPosition() throws IOException {
        this.count = this.postings.freq();
        this.nextPosition();
    }

    final boolean nextPosition() throws IOException {
        if (this.count-- > 0) {
            this.position = this.postings.nextPosition() - this.offset;
            return true;
        } else {
            return false;
        }
    }

    public String toString() {
        String s = "o:" + this.offset + " p:" + this.position + " c:" + this.count;
        if (this.rptGroup >= 0) {
            s = s + " rpt:" + this.rptGroup + ",i" + this.rptInd;
        }

        return s;
    }
}
