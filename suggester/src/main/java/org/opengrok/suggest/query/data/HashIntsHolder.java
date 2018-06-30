package org.opengrok.suggest.query.data;

import java.util.HashSet;

public class HashIntsHolder extends HashSet<Integer> implements IntsHolder {

    @Override
    public boolean has(int i) {
        return contains(i);
    }

}
