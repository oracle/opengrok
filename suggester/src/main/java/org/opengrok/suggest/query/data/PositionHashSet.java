package org.opengrok.suggest.query.data;

import java.util.HashSet;

public class PositionHashSet extends HashSet<Integer> implements PositionSet {

    @Override
    public boolean isSet(int pos) {
        return contains(pos);
    }

}
