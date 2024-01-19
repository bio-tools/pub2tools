/*
 * Copyright © 2019, 2020 Erik Jaaniso
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

package tools.bio.pub2tools.core;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
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

	private static Map<String, String> getQuery(String resultType, String cursorMark, String date, String source, String search, String custom, String not, FetcherArgs fetcherArgs) throws URISyntaxException {
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
		query.put("query", date + (source != null ? " AND " + source : "") + (search != null ? " AND " + search : "") + (custom != null ? " AND " + custom : "") + (not != null ? " AND " + not : ""));
		return query;
	}

	private static List<PublicationIds> getIds(String type, String resultType, String date, String source, String search, String custom, String not, FetcherArgs fetcherArgs, String logPrefix) throws IOException, ParseException, URISyntaxException {
		Marker mainMarker = MarkerManager.getMarker(Common.MAIN_MARKER);

		List<PublicationIds> ids = new ArrayList<>();

		int expectedSize = -1;
		int resultSize = 0;
		String cursorMark = "*";

		int pageIndex = 0;
		long start = System.currentTimeMillis();

		try (Fetcher fetcher = new Fetcher(fetcherArgs.getPrivateArgs())) {
			Map<String, String> query = getQuery(resultType, cursorMark, date, source, search, custom, not, fetcherArgs);
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

						query = getQuery(resultType, cursorMark, date, source, search, custom, not, fetcherArgs);
						doc = fetcher.postDoc("https://www.ebi.ac.uk/europepmc/webservices/rest/searchPOST", query, fetcherArgs);
					}
				} else {
					int expectedIndex = (expectedSize - 1) / 1000 + 1;
					if (expectedIndex > 1 && pageIndex != expectedIndex) {
						logger.error(mainMarker, "{}Tag nextCursorMark not found in {}", logPrefix, doc.location());
					}
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
				if (resultSize > 0) {
					logger.error(mainMarker, "{}Less results have been returned ({}) than expected ({}) in {}", logPrefix, resultSize, expectedSize, doc.location());
				} else {
					throw new RuntimeException("Less results have been returned (" + resultSize + ") than expected (" + expectedSize + ") for query " + query);
				}
			}
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

	private static Set<PublicationIds> abstractQuery(String resultType, String date, String source, String custom, String not, FetcherArgs fetcherArgs, String logPrefix) throws IOException, ParseException, URISyntaxException {
		Marker mainMarker = MarkerManager.getMarker(Common.MAIN_MARKER);
		logger.info(mainMarker, "{}Running abstract query for source {} and date {}", logPrefix, source, date);

		String excellent = getAbstractQuery("excellent");
		String good = getAbstractQuery("good");
		String mediocre1 = getAbstractQuery("mediocre1");
		String mediocre2 = getAbstractQuery("mediocre2");
		String http = getAbstractQuery("http");
		String toolGood = getAbstractQueryPlural("tool_good");
		String tool = getAbstractQueryPlural("tool");

		List<String> toolsGood = PubFetcher.getResource(SelectPub.class, "select/tool_good.txt");
		List<String> tools = PubFetcher.getResource(SelectPub.class, "select/tool.txt");

		List<PublicationIds> excellentIds = getIds("excellent", resultType, date, source, excellent, custom, not, fetcherArgs, logPrefix);
		List<PublicationIds> goodHttpIds = getIds("good + http", resultType, date, source, good + " AND " + http, custom, not, fetcherArgs, logPrefix);
		List<PublicationIds> goodToolGoodIds = getIds("good + tool_good", resultType, date, source, good + " AND " + toolGood, custom, not, fetcherArgs, logPrefix);
		List<PublicationIds> mediocre1HttpToolIds = getIds("mediocre1 + http + tool", resultType, date, source, mediocre1 + " AND " + http + " AND " + tool, custom, not, fetcherArgs, logPrefix);
		List<PublicationIds> mediocre2HttpToolIds = getIds("mediocre2 + http + tool", resultType, date, source, mediocre2 + " AND " + http + " AND " + tool, custom, not, fetcherArgs, logPrefix);
		List<PublicationIds> mediocre1ToolGoodToolIds = getIds("mediocre1 + tool_good + tool", resultType, date, source, mediocre1 + " AND " + toolGood + " AND " + tool, custom, not, fetcherArgs, logPrefix);
		List<PublicationIds> mediocre2ToolGoodToolIds = getIds("mediocre2 + tool_good + tool", resultType, date, source, mediocre2 + " AND " + toolGood + " AND " + tool, custom, not, fetcherArgs, logPrefix);
		List<PublicationIds> httpToolGoodIds = getIds("http + tool_good", resultType, date, source, http + " AND " + toolGood, custom, not, fetcherArgs, logPrefix);

		List<List<PublicationIds>> toolGoodIds = new ArrayList<>();
		for (String t : toolsGood) {
			toolGoodIds.add(getIds("\"" + t + "\"", resultType, date, source, "(ABSTRACT:\"" + t + "\" OR ABSTRACT:\"" + getPlural(t) + "\")", custom, not, fetcherArgs, logPrefix));
		}
		List<List<PublicationIds>> toolIds = new ArrayList<>();
		for (String t : tools) {
			toolIds.add(getIds("\"" + t + "\"", resultType, date, source, "(ABSTRACT:\"" + t + "\" OR ABSTRACT:\"" + getPlural(t) + "\")", custom, not, fetcherArgs, logPrefix));
		}

		List<PublicationIds> goodIds = getIds("good", resultType, date, source, good, custom, not, fetcherArgs, logPrefix);
		Set<PublicationIds> mediocreIds = new LinkedHashSet<>();
		mediocreIds.addAll(getIds("mediocre1", resultType, date, source, mediocre1, custom, not, fetcherArgs, logPrefix));
		mediocreIds.addAll(getIds("mediocre2", resultType, date, source, mediocre2, custom, not, fetcherArgs, logPrefix));
		List<PublicationIds> httpIds = getIds("http", resultType, date, source, http, custom, not, fetcherArgs, logPrefix);

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
		int mediocreIdsI = 0;
		for (PublicationIds mediocreId : mediocreIds) {
			System.err.print(PubFetcher.progress(mediocreIdsI + 1, mediocreIds.size(), start) + "  \r");
			int j = findId(0, 2, toolIds, mediocreId);
			if (j >= 0) {
				j = findId(j + 1, 1, toolIds, mediocreId);
				if (j >= 0) {
					findIdLast(j + 1, toolIds, mediocreId, mediocreToolToolToolIds);
				}
			}
			++mediocreIdsI;
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
		ids.addAll(mediocre1HttpToolIds);
		ids.addAll(mediocre2HttpToolIds);
		ids.addAll(mediocre1ToolGoodToolIds);
		ids.addAll(mediocre2ToolGoodToolIds);
		ids.addAll(httpToolGoodIds);
		ids.addAll(goodToolToolIds);
		ids.addAll(mediocreToolToolToolIds);
		ids.addAll(httpToolToolIds);
		ids.addAll(toolGoodToolGoodIds);
		ids.addAll(toolGoodToolToolIds);

		logger.info(mainMarker, "{}Abstract query for source {} and date {} returned {} results", logPrefix, source, date, ids.size());
		return ids;
	}

	public static String getDate(String from, String to, String month, String day, String reason) {
		if (from != null && to == null) {
			throw new IllegalArgumentException("If --from is specified, then --to must also be specified!");
		}
		if (from == null && to != null) {
			throw new IllegalArgumentException("If --to is specified, then --from must also be specified!");
		}
		if ((from == null || to == null) && month == null && day == null) {
			throw new IllegalArgumentException("Parameters --from/--to or --month or --day are required" + reason + "!");
		}
		if ((from != null && to != null) && (month != null || day != null)
				|| (month != null && day != null)) {
			throw new IllegalArgumentException("Specify only one of --from/--to or --month or --day" + reason + "!");
		}
		if (from != null && to != null) {
			LocalDate fromDate = LocalDate.parse(from, DateTimeFormatter.ISO_LOCAL_DATE);
			LocalDate toDate = LocalDate.parse(to, DateTimeFormatter.ISO_LOCAL_DATE);
			if (toDate.isBefore(fromDate)) {
				throw new IllegalArgumentException("The date in --to cannot be earlier than the date in --from!");
			}
			return "CREATION_DATE:[" + fromDate.toString() + " TO " + toDate.toString() + "]";
		}
		if (month != null) {
			LocalDate monthBegin = LocalDate.parse(month + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
			LocalDate monthEnd = monthBegin.with(TemporalAdjusters.lastDayOfMonth());
			return "CREATION_DATE:[" + monthBegin.toString() + " TO " + monthEnd.toString() + "]";
		}
		LocalDate dayDate = LocalDate.parse(day, DateTimeFormatter.ISO_LOCAL_DATE);
		return "CREATION_DATE:" + dayDate.toString();
	}

	public static Set<PublicationIds> select(String date, boolean disableTool, String custom, boolean disableNot, FetcherArgs fetcherArgs, String logPrefix) throws IOException, ParseException, URISyntaxException {
		Marker mainMarker = MarkerManager.getMarker(Common.MAIN_MARKER);

		if (custom != null) {
			logger.info(mainMarker, "{}Using custom restriction for all queries: {}", logPrefix, custom);
			custom = "(" + custom + ")";
		}

		String not = null;
		if (!disableNot) {
			List<String> notAbstract = PubFetcher.getResource(SelectPub.class, "select/not_abstract.txt");
			List<String> notTitle = PubFetcher.getResource(SelectPub.class, "select/not_title.txt");
			not = "(NOT " + notAbstract.stream().map(n -> "ABSTRACT:\"" + n + "\"").collect(Collectors.joining(" NOT "));
			not += " NOT " + notTitle.stream().map(n -> "TITLE:\"" + n + "\"").collect(Collectors.joining(" NOT ")) + ")";
		} else {
			logger.info(mainMarker, "{}Exclusion usage of not_abstract.txt and not_title.txt disabled", logPrefix, custom);
		}

		Set<PublicationIds> ids = new LinkedHashSet<>();

		if (!disableTool) {
			logger.info(mainMarker, "{}Running journal list query for date {}", logPrefix, date);
			List<String> journalList = PubFetcher.getResource(SelectPub.class, "select/journal.txt");
			String journalSearch = "(" + journalList.stream().map(j -> "JOURNAL:\"" + j + "\"").collect(Collectors.joining(" OR ")) + ")";
			List<PublicationIds> idsJournal = getIds("journal list", "idlist", date, null, journalSearch, custom, not, fetcherArgs, logPrefix);
			logger.info(mainMarker, "{}Journal list query for date {} returned {} results", logPrefix, date, idsJournal.size());
			ids.addAll(idsJournal);
		}

		if (disableTool) {
			ids.addAll(getIds("unrestricted (to tools) from (SRC:MED OR SRC:PMC)", "idlist", date, "(SRC:MED OR SRC:PMC)", null, custom, not, fetcherArgs, logPrefix));
			ids.addAll(getIds("unrestricted (to tools) from (SRC:PPR)", "lite", date, "(SRC:PPR)", null, custom, not, fetcherArgs, logPrefix));
		} else {
			ids.addAll(abstractQuery("idlist", date, "(SRC:MED OR SRC:PMC)", custom, not, fetcherArgs, logPrefix));
			ids.addAll(abstractQuery("lite", date, "(SRC:PPR)", custom, not, fetcherArgs, logPrefix));
		}

		return ids;
	}
}
