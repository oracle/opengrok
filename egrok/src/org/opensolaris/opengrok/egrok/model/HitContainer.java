package org.opensolaris.opengrok.egrok.model;

import java.util.ArrayList;
import java.util.List;

public class HitContainer {
	private String name;
	private List<Hit> hits = new ArrayList<Hit>();

	public HitContainer(String name) {
		this.name = name;

	}

	public void add(Hit hit) {
		hits.add(hit);
	}

	public String getName() {
		return name;
	}

	public int getNumberOfHits() {
		return hits.size();
	}

	public Hit[] getHits() {
		return hits.toArray(new Hit[hits.size()]);
	}
}
