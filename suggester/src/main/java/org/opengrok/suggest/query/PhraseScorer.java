package org.opengrok.suggest.query;

import java.util.Map;
import java.util.Set;

public interface PhraseScorer {

    Map<Integer, Set<Integer>> getMap();

}
