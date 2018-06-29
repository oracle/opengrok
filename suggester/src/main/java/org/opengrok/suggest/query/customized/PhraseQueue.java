//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.opengrok.suggest.query.customized;

import org.apache.lucene.util.PriorityQueue;

final class PhraseQueue extends PriorityQueue<PhrasePositions> {

    PhraseQueue(int size) {
        super(size);
    }

    protected final boolean lessThan(PhrasePositions pp1, PhrasePositions pp2) {
        if (pp1.position == pp2.position) {
            if (pp1.offset == pp2.offset) {
                return pp1.ord < pp2.ord;
            } else {
                return pp1.offset < pp2.offset;
            }
        } else {
            return pp1.position < pp2.position;
        }
    }
}
