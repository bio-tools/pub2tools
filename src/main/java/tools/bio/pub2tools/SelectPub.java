/*
 * Copyright © 2019 Erik Jaaniso
 *
 * This file is part of Pub2Tools.
 *
 * Pub2Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Pub2Tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pub2Tools.  If not, see <http://www.gnu.org/licenses/>.
 */

package tools.bio.pub2tools;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.edamontology.pubfetcher.core.common.FetcherArgs;
import org.edamontology.pubfetcher.core.common.PubFetcher;
import org.edamontology.pubfetcher.core.db.publication.PublicationIds;
import org.edamontology.pubfetcher.core.fetching.Fetcher;

public class SelectPub {

	private static final Logger logger = LogManager.getLogger();

	private static Map<String, String> getQuery(String resultType, String cursorMark, String search, String source, String date, FetcherArgs fetcherArgs) throws URISyntaxException {
		Map<String, String> query = new LinkedHashMap<>();
		query.put("resultType", resultType);
		query.put("cursorMark", cursorMark);
		query.put("pageSize", "1000");
		query.put("sort", "ID asc");
		query.put("format", "xml");
		String email = fetcherArgs.getPrivateArgs().getEuropepmcEmail();
		if (email != null && !email.isEmpty()) {
			query.put("email", email);
		}
		if (source != null) {
			query.put("query", search + " AND " + source + " AND " + date);
		} else {
			query.put("query", search + " AND " + date);
		}
		return query;
	}

	private static List<PublicationIds> getIds(String type, String resultType, String search, String source, String date, FetcherArgs fetcherArgs, String logPrefix) throws IOException, ParseException, URISyntaxException {
		Marker mainMarker = MarkerManager.getMarker(Pub2Tools.MAIN_MARKER);

		List<PublicationIds> ids = new ArrayList<>();

		int expectedSize = -1;
		int resultSize = 0;
		String cursorMark = "*";

		int pageIndex = 0;
		long start = System.currentTimeMillis();

		Fetcher fetcher = new Fetcher(fetcherArgs.getPrivateArgs());
		Map<String, String> query = getQuery(resultType, cursorMark, search, source, date, fetcherArgs);
		Document doc = fetcher.postDoc("https://www.ebi.ac.uk/europepmc/webservices/rest/searchPOST", query, fetcherArgs);
		if (doc == null) {
			throw new RuntimeException("No Document returned for query " + query);
		}

		while (doc != null) {
			int initialSize = resultSize;
			for (Element result : doc.select("resultList > result")) {
				Element pmid = result.getElementsByTag("pmid").first();
				String pmidText = (pmid != null ? pmid.text() : null);
				Element pmcid = result.getElementsByTag("pmcid").first();
				String pmcidText = (pmcid != null ? pmcid.text() : null);
				Element doi = result.getElementsByTag("doi").first();
				String doiText = (doi != null ? doi.text() : null);
				ids.add(new PublicationIds(pmidText, pmcidText, doiText, pmidText != null ? doc.location() : null, pmcidText != null ? doc.location() : null, doiText != null ? doc.location() : null));
				++resultSize;
			}
			if (expectedSize < 0) {
				Element hitCount = doc.getElementsByTag("hitCount").first();
				if (hitCount != null) {
					try {
						expectedSize = Integer.parseInt(hitCount.text());
						logger.info(mainMarker, "{}Getting {} results for {}", logPrefix, expectedSize, type);
					} catch(NumberFormatException e) {
						throw new RuntimeException("Tag hitCount does not contain an integer in " + doc.location());
					}
				} else {
					throw new RuntimeException("Tag hitCount not found in " + doc.location());
				}
			}
			if (resultSize > expectedSize) {
				throw new RuntimeException("More results have been returned (" + resultSize + ") than expected (" + expectedSize + ") in " + doc.location());
			}
			Element nextCursorMark = doc.getElementsByTag("nextCursorMark").first();
			if (nextCursorMark != null) {
				String nextCursorMarkText = nextCursorMark.text();
				if (nextCursorMarkText.equals(cursorMark)) {
					break;
				} else {
					cursorMark = nextCursorMarkText;

					++pageIndex;
					System.err.print(PubFetcher.progress(pageIndex, (expectedSize - 1) / 1000 + 1, start) + "  \r");

					query = getQuery(resultType, cursorMark, search, source, date, fetcherArgs);
					doc = fetcher.postDoc("https://www.ebi.ac.uk/europepmc/webservices/rest/searchPOST", query, fetcherArgs);
				}
			} else {
				logger.error(mainMarker, "{}Tag nextCursorMark not found in {}", logPrefix, doc.location());
				break;
			}
			if (resultSize == initialSize) {
				logger.error(mainMarker, "{}Result size did not increase in {}", logPrefix, doc.location());
				break;
			}
		}

		if (doc == null) {
			logger.error(mainMarker, "{}No Document returned for query {}", logPrefix, query);
		}
		if (resultSize < expectedSize) {
			throw new RuntimeException("Less results have been returned (" + resultSize + ") than expected (" + expectedSize + ") for query " + query);
		}

		return ids;
	}

	// plural is complex, this includes only what is necessary for select/tool.txt and select/tool_good.txt
	private static String getPlural(String singular) {
		if (singular.endsWith("x") || singular.endsWith("ch")) {
			return singular + "es";
		} else if (singular.endsWith("y")) {
			return singular.substring(0, singular.length() - 1) + "ies";
		} else {
			return singular + "s";
		}
	}

	private static String getAbstractQuery(String resource) throws IOException {
		return "(" + PubFetcher.getResource(SelectPub.class, "select/" + resource + ".txt").stream().map(s -> "ABSTRACT:\"" + s + "\"").collect(Collectors.joining(" OR ")) + ")";
	}
	private static String getAbstractQueryPlural(String resource) throws IOException {
		return "(" + PubFetcher.getResource(SelectPub.class, "select/" + resource + ".txt").stream().map(s -> "ABSTRACT:\"" + s + "\" OR ABSTRACT:\"" + getPlural(s) + "\"").collect(Collectors.joining(" OR ")) + ")";
	}

	private static int findId(int from, int fromLast, List<List<PublicationIds>> toolIds, PublicationIds firstId) {
		for (int i = from; i < toolIds.size() - fromLast; ++i) {
			for (PublicationIds toolId : toolIds.get(i)) {
				if (!toolId.getPmid().isEmpty() && toolId.getPmid().equals(firstId.getPmid())
						|| !toolId.getPmcid().isEmpty() && toolId.getPmcid().equals(firstId.getPmcid())
						|| !toolId.getDoi().isEmpty() && toolId.getDoi().equals(firstId.getDoi())) {
					return i;
				}
			}
		}
		return -1;
	}
	private static void findIdLast(int from, List<List<PublicationIds>> toolIds, PublicationIds firstId, List<PublicationIds> ids) {
		for (int i = from; i < toolIds.size(); ++i) {
			for (PublicationIds toolId : toolIds.get(i)) {
				if (!toolId.getPmid().isEmpty() && toolId.getPmid().equals(firstId.getPmid())
						|| !toolId.getPmcid().isEmpty() && toolId.getPmcid().equals(firstId.getPmcid())
						|| !toolId.getDoi().isEmpty() && toolId.getDoi().equals(firstId.getDoi())) {
					ids.add(firstId);
					return;
				}
			}
		}
	}

	private static Set<PublicationIds> abstractQuery(String resultType, String source, String date, FetcherArgs fetcherArgs, String logPrefix) throws IOException, ParseException, URISyntaxException {
		Marker mainMarker = MarkerManager.getMarker(Pub2Tools.MAIN_MARKER);
		logger.info(mainMarker, "{}Running abstract query for source {} and date {}", logPrefix, source, date);

		String excellent = getAbstractQuery("excellent");
		String good = getAbstractQuery("good");
		String mediocre = getAbstractQuery("mediocre");
		String http = getAbstractQuery("http");
		String toolGood = getAbstractQueryPlural("tool_good");
		String tool = getAbstractQueryPlural("tool");

		List<String> toolsGood = PubFetcher.getResource(SelectPub.class, "select/tool_good.txt");
		List<String> tools = PubFetcher.getResource(SelectPub.class, "select/tool.txt");

		List<PublicationIds> excellentIds = getIds("excellent", resultType, excellent, source, date, fetcherArgs, logPrefix);
		List<PublicationIds> goodHttpIds = getIds("good + http", resultType, good + " AND " + http, source, date, fetcherArgs, logPrefix);
		List<PublicationIds> goodToolGoodIds = getIds("good + tool_good", resultType, good + " AND " + toolGood, source, date, fetcherArgs, logPrefix);
		List<PublicationIds> mediocreHttpToolIds = getIds("mediocre + http + tool", resultType, mediocre + " AND " + http + " AND " + tool, source, date, fetcherArgs, logPrefix);
		List<PublicationIds> mediocreToolGoodToolIds = getIds("mediocre + tool_good + tool", resultType, mediocre + " AND " + toolGood + " AND " + tool, source, date, fetcherArgs, logPrefix);
		List<PublicationIds> httpToolGoodIds = getIds("http + tool_good", resultType, http + " AND " + toolGood, source, date, fetcherArgs, logPrefix);

		List<List<PublicationIds>> toolGoodIds = new ArrayList<>();
		for (String t : toolsGood) {
			toolGoodIds.add(getIds("\"" + t + "\"", resultType, "(ABSTRACT:\"" + t + "\" OR ABSTRACT:\"" + getPlural(t) + "\")", source, date, fetcherArgs, logPrefix));
		}
		List<List<PublicationIds>> toolIds = new ArrayList<>();
		for (String t : tools) {
			toolIds.add(getIds("\"" + t + "\"", resultType, "(ABSTRACT:\"" + t + "\" OR ABSTRACT:\"" + getPlural(t) + "\")", source, date, fetcherArgs, logPrefix));
		}

		List<PublicationIds> goodIds = getIds("good", resultType, good, source, date, fetcherArgs, logPrefix);
		List<PublicationIds> mediocreIds = getIds("mediocre", resultType, mediocre, source, date, fetcherArgs, logPrefix);
		List<PublicationIds> httpIds = getIds("http", resultType, http, source, date, fetcherArgs, logPrefix);

		logger.info(mainMarker, "{}Getting results for good + tool + tool", logPrefix);
		List<PublicationIds> goodToolToolIds = new ArrayList<>();
		long start = System.currentTimeMillis();
		for (int i = 0; i < goodIds.size(); ++i) {
			System.err.print(PubFetcher.progress(i + 1, goodIds.size(), start) + "  \r");
			PublicationIds goodId = goodIds.get(i);
			int j = findId(0, 1, toolIds, goodId);
			if (j >= 0) {
				findIdLast(j + 1, toolIds, goodId, goodToolToolIds);
			}
		}
		logger.info(mainMarker, "{}Got {} results for good + tool + tool", logPrefix, goodToolToolIds.size());

		logger.info(mainMarker, "{}Getting results for mediocre + tool + tool + tool", logPrefix);
		List<PublicationIds> mediocreToolToolToolIds = new ArrayList<>();
		start = System.currentTimeMillis();
		for (int i = 0; i < mediocreIds.size(); ++i) {
			System.err.print(PubFetcher.progress(i + 1, mediocreIds.size(), start) + "  \r");
			PublicationIds mediocreId = mediocreIds.get(i);
			int j = findId(0, 2, toolIds, mediocreId);
			if (j >= 0) {
				j = findId(j + 1, 1, toolIds, mediocreId);
				if (j >= 0) {
					findIdLast(j + 1, toolIds, mediocreId, mediocreToolToolToolIds);
				}
			}
		}
		logger.info(mainMarker, "{}Got {} results for mediocre + tool + tool + tool", logPrefix, mediocreToolToolToolIds.size());

		logger.info(mainMarker, "{}Getting results for http + tool + tool", logPrefix);
		List<PublicationIds> httpToolToolIds = new ArrayList<>();
		start = System.currentTimeMillis();
		for (int i = 0; i < httpIds.size(); ++i) {
			System.err.print(PubFetcher.progress(i + 1, httpIds.size(), start) + "  \r");
			PublicationIds httpId = httpIds.get(i);
			int j = findId(0, 1, toolIds, httpId);
			if (j >= 0) {
				findIdLast(j + 1, toolIds, httpId, httpToolToolIds);
			}
		}
		logger.info(mainMarker, "{}Got {} results for http + tool + tool", logPrefix, httpToolToolIds.size());

		int toolGoodIdsSize = 0;
		for (List<PublicationIds> toolGoodId : toolGoodIds) {
			toolGoodIdsSize += toolGoodId.size();
		}

		logger.info(mainMarker, "{}Getting results for tool_good + tool_good", logPrefix);
		List<PublicationIds> toolGoodToolGoodIds = new ArrayList<>();
		int toolGoodIndex = 0;
		start = System.currentTimeMillis();
		for (int i = 0; i < toolGoodIds.size() - 1; ++i) {
			for (PublicationIds firstToolGoodId : toolGoodIds.get(i)) {
				++toolGoodIndex;
				System.err.print(PubFetcher.progress(toolGoodIndex, toolGoodIdsSize, start) + "  \r");
				if (!toolGoodToolGoodIds.contains(firstToolGoodId)) {
					findIdLast(i + 1, toolGoodIds, firstToolGoodId, toolGoodToolGoodIds);
				}
			}
		}
		logger.info(mainMarker, "{}Got {} results for tool_good + tool_good", logPrefix, toolGoodToolGoodIds.size());

		logger.info(mainMarker, "{}Getting results for tool_good + tool + tool", logPrefix);
		List<PublicationIds> toolGoodToolToolIds = new ArrayList<>();
		toolGoodIndex = 0;
		start = System.currentTimeMillis();
		for (List<PublicationIds> toolGoodId : toolGoodIds) {
			for (PublicationIds firstToolGoodId : toolGoodId) {
				++toolGoodIndex;
				System.err.print(PubFetcher.progress(toolGoodIndex, toolGoodIdsSize, start) + "  \r");
				if (!toolGoodToolToolIds.contains(firstToolGoodId)) {
					int i = findId(0, 1, toolIds, firstToolGoodId);
					if (i >= 0) {
						findIdLast(i + 1, toolIds, firstToolGoodId, toolGoodToolToolIds);
					}
				}
			}
		}
		logger.info(mainMarker, "{}Got {} results for tool_good + tool + tool", logPrefix, toolGoodToolToolIds.size());

		Set<PublicationIds> ids = new LinkedHashSet<>();
		ids.addAll(excellentIds);
		ids.addAll(goodHttpIds);
		ids.addAll(goodToolGoodIds);
		ids.addAll(mediocreHttpToolIds);
		ids.addAll(mediocreToolGoodToolIds);
		ids.addAll(httpToolGoodIds);
		ids.addAll(goodToolToolIds);
		ids.addAll(mediocreToolToolToolIds);
		ids.addAll(httpToolToolIds);
		ids.addAll(toolGoodToolGoodIds);
		ids.addAll(toolGoodToolToolIds);

		logger.info(mainMarker, "{}Abstract query for source {} and date {} returned {} results", logPrefix, source, date, ids.size());
		return ids;
	}

	public static String getDate(String from, String to) {
		LocalDate fromDate = LocalDate.parse(from, DateTimeFormatter.ISO_LOCAL_DATE);
		LocalDate toDate = LocalDate.parse(to, DateTimeFormatter.ISO_LOCAL_DATE);
		if (toDate.isBefore(fromDate)) {
			throw new IllegalArgumentException("The date in --to cannot be earlier than the date in --from!");
		}
		return "CREATION_DATE:[" + fromDate.toString() + " TO " + toDate.toString() + "]";
	}

	public static Set<PublicationIds> select(String date, FetcherArgs fetcherArgs, String logPrefix) throws IOException, ParseException, URISyntaxException {
		Marker mainMarker = MarkerManager.getMarker(Pub2Tools.MAIN_MARKER);

		logger.info(mainMarker, "{}Running journal list query for date {}", logPrefix, date);
		List<String> journalList = PubFetcher.getResource(SelectPub.class, "select/journal.txt");
		String journalSearch = "(" + journalList.stream().map(j -> "JOURNAL:\"" + j + "\"").collect(Collectors.joining(" OR ")) + ")";
		List<PublicationIds> idsJournal = getIds("journal list", "idlist", journalSearch, null, date, fetcherArgs, logPrefix);
		logger.info(mainMarker, "{}Journal list query for date {} returned {} results", logPrefix, date, idsJournal.size());

		Set<PublicationIds> idsMed = abstractQuery("idlist", "(SRC:MED OR SRC:PMC)", date, fetcherArgs, logPrefix);
		Set<PublicationIds> idsPpr = abstractQuery("lite", "(SRC:PPR)", date, fetcherArgs, logPrefix);

		Set<PublicationIds> ids = new LinkedHashSet<>();
		ids.addAll(idsJournal);
		ids.addAll(idsMed);
		ids.addAll(idsPpr);

		return ids;
	}
}
