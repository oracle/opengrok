package org.opengrok.suggest.query;

import org.opengrok.suggest.query.data.PositionSet;

public interface PhraseScorer {

    PositionSet getPositions(int docId);

}
