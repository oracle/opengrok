package org.opengrok.suggest.query.data;

import java.util.BitSet;

public class BitIntsHolder extends BitSet implements IntsHolder {

    @Override
    public boolean has(final int i) {
        return get(i);
    }

}
