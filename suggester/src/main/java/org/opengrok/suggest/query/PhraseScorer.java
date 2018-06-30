package org.opengrok.suggest.query;

import org.opengrok.suggest.query.data.IntsHolder;

public interface PhraseScorer {

    IntsHolder getPositions(int docId);

}
