package org.opengrok.suggest;

import org.apache.lucene.util.PriorityQueue;

import java.util.Arrays;
import java.util.List;

class LookupPriorityQueue extends PriorityQueue<LookupResultItem> {

    LookupPriorityQueue(final int maxSize) {
        super(maxSize);
    }

    @Override
    protected boolean lessThan(final LookupResultItem item1, final LookupResultItem item2) {
        return item1.getWeight() < item2.getWeight();
    }

    List<LookupResultItem> getResult() {
        int size = this.size();
        LookupResultItem[] res = new LookupResultItem[size];

        for (int i = size - 1; i >= 0; i--) { // iterate from top so results are ordered in descending order
            res[i] = this.pop();
        }

        return Arrays.asList(res);
    }

}
