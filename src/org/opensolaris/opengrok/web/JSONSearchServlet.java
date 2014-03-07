package org.opensolaris.opengrok.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tools.ant.util.Base64Converter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.opensolaris.opengrok.search.Hit;
import org.opensolaris.opengrok.search.SearchEngine;

public class JSONSearchServlet extends HttpServlet {
	private static final long serialVersionUID = -1675062445999680962L;
	private static final Base64Converter conv = new Base64Converter();
	private static final int MAX_RESULTS = 1000; // hard coded limit
	private static final String PARAM_FREETEXT = "freetext";
	private static final String PARAM_DEF = "def";
	private static final String PARAM_SYMBOL = "symbol";
	private static final String PARAM_PATH = "path";
	private static final String PARAM_HIST = "hist";
	private static final String PARAM_MAXRESULTS = "maxresults";
	private static final String ATTRIBUTE_DIRECTORY = "directory";
	private static final String ATTRIBUTE_FILENAME = "filename";
	private static final String ATTRIBUTE_LINENO = "lineno";
	private static final String ATTRIBUTE_LINE = "line";
	private static final String ATTRIBUTE_PATH = "path";
	private static final String ATTRIBUTE_RESULTS = "results";

	@SuppressWarnings("unchecked")
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		JSONObject result = new JSONObject();
		SearchEngine engine = new SearchEngine();

		boolean valid = false;

		String freetext = req.getParameter(PARAM_FREETEXT);
		String def = req.getParameter(PARAM_DEF);
		String symbol = req.getParameter(PARAM_SYMBOL);
		String path = req.getParameter(PARAM_PATH);
		String hist = req.getParameter(PARAM_HIST);

		if (freetext != null) {
			engine.setFreetext(freetext);
			valid = true;
			result.put(PARAM_FREETEXT, freetext);
		}

		if (def != null) {
			engine.setDefinition(def);
			valid = true;
			result.put(PARAM_DEF, def);
		}

		if (symbol != null) {
			engine.setSymbol(symbol);
			valid = true;
			result.put(PARAM_SYMBOL, symbol);
		}

		if (path != null) {
			engine.setFile(path);
			valid = true;
			result.put(PARAM_PATH, path);
		}

		if (hist != null) {
			engine.setHistory(hist);
			valid = true;
			result.put(PARAM_HIST, hist);
		}

		if (valid) {
			int numResults = engine.search();
			int maxResults = MAX_RESULTS;
			String maxResultsParam = req.getParameter(PARAM_MAXRESULTS);
			if (maxResultsParam != null) {
				try {
					maxResults = Integer.parseInt(maxResultsParam);
				} catch (NumberFormatException ex) {
				}
			}
			List<Hit> results = new ArrayList<Hit>(maxResults);
			engine.results(0,
					numResults > maxResults ? maxResults : numResults, results);
			JSONArray resultsArray = new JSONArray();
			for (Hit hit : results) {
				JSONObject hitJson = new JSONObject();
				hitJson.put(ATTRIBUTE_DIRECTORY,
						JSONObject.escape(hit.getDirectory()));
				hitJson.put(ATTRIBUTE_FILENAME,
						JSONObject.escape(hit.getFilename()));
				hitJson.put(ATTRIBUTE_LINENO, hit.getLineno());
				hitJson.put(ATTRIBUTE_LINE, conv.encode(hit.getLine()));
				hitJson.put(ATTRIBUTE_PATH, hit.getPath());
				resultsArray.add(hitJson);
			}
			result.put(ATTRIBUTE_RESULTS, resultsArray);
		}
		resp.getWriter().write(result.toString());
	}
}
