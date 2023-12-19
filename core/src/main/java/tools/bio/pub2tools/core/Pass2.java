/*
 * Copyright © 2018, 2019, 2023 Erik Jaaniso
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import org.edamontology.pubfetcher.core.common.FetcherArgs;
import org.edamontology.pubfetcher.core.common.PubFetcher;
import org.edamontology.pubfetcher.core.db.Database;
import org.edamontology.pubfetcher.core.db.publication.CorrespAuthor;
import org.edamontology.pubfetcher.core.db.publication.Publication;
import org.edamontology.pubfetcher.core.db.publication.PublicationIds;
import org.edamontology.pubfetcher.core.db.webpage.Webpage;
import org.edamontology.pubfetcher.core.scrape.Scrape;

import org.edamontology.edammap.core.idf.Idf;
import org.edamontology.edammap.core.input.Json;
import org.edamontology.edammap.core.input.json.Credit;
import org.edamontology.edammap.core.input.json.DocumentationType;
import org.edamontology.edammap.core.input.json.DownloadType;
import org.edamontology.edammap.core.input.json.EntityType;
import org.edamontology.edammap.core.input.json.Link;
import org.edamontology.edammap.core.input.json.LinkDownload;
import org.edamontology.edammap.core.input.json.LinkType;
import org.edamontology.edammap.core.input.json.Tool;
import org.edamontology.edammap.core.preprocessing.PreProcessor;
import org.edamontology.edammap.core.query.QueryType;

import tools.bio.pub2tools.core.Language.LanguageSearch;
import tools.bio.pub2tools.core.License.LicenseSearch;

public final class Pass2 {

	private static final Logger logger = LogManager.getLogger();

	public static final String TOOL_STATUS = "status";

	private static final double ABSTRACT_LINK_INCREASE = 1000;
	private static final double FULLTEXT_LINK_INCREASE = 500;
	private static final double FROM_ABSTRACT_LINK_INCREASE = 400;
	private static final double NOT_FIRST_SUGGESTION_LINK_DIVIDER = 2;

	private static final double TOOL_TITLE_1_INCREASE = 900;
	private static final double TOOL_TITLE_2_INCREASE = 600;
	private static final double TOOL_TITLE_3_INCREASE = 400;
	private static final double TOOL_TITLE_4_INCREASE = 250;
	private static final double TOOL_TITLE_5_INCREASE = 150;
	private static final int TOOL_TITLE_ORIGINAL_MAX_SIZE_FOR_ACRONYM = 6;

	private static final Pattern CASE_REMOVE_HYPHEN = Pattern.compile("^(\\p{Lu}.*)-(\\p{Lu})(.*)$");
	private static final Pattern CASE_REMOVE_PLURAL = Pattern.compile("^(.*\\p{Lu})s$");
	private static final Pattern CASE_LOWERCASE = Pattern.compile("^\\p{Ll}+$");
	private static final Pattern CASE_FIRST_CAPITAL = Pattern.compile("^\\p{Lu}\\p{Ll}+$");
	private static final Pattern CASE_UPPERCASE = Pattern.compile("^\\p{Lu}+$");
	private static final Pattern CASE_MIXED_AS_REST = Pattern.compile("^.*\\p{L}.*$");
	private static final double CASE_LOWERCASE_INCREASE = 100;
	private static final double CASE_FIRST_CAPITAL_INCREASE = 200;
	private static final double CASE_UPPERCASE_INCREASE = 300;
	private static final double CASE_MIXED_INCREASE = 400;
	private static final double CASE_DECREASE = 100;
	private static final double SCORE_MIN_FOR_MIXED_IDF_INCREASE = 3.5;
	private static final double SCORE_MIN_FOR_UPPERCASE_IDF_INCREASE = 7;
	private static final double SCORE_MIN_FOR_MIXED_IDF_INCREASE2 = 12.1;

	private static final double IDF_POWER = 5;
	private static final double IDF_MULTIPLIER = 500;

	private static final int NAME_WORD_MATCH_LIMIT = 5;

	private static final Pattern NOT_ALPHANUM = Pattern.compile("[^\\p{L}\\p{N}]");

	private static final Pattern HOMEPAGE_EXCLUDE = Pattern.compile("(?i)^(https?://)?(www\\.)?(clinicaltrials\\.gov|osf\\.io|annualreviews\\.org|w3\\.org|creativecommons\\.org|data\\.mendeley\\.com|ncbi\\.nlm\\.nih\\.gov/.+=GSE[0-9]+)([^\\p{L}]|$)");
	private static final Pattern JOURNAL_EXCLUDE = Pattern.compile("(?i)^(Systematic reviews|The Cochrane Database of Systematic Reviews|Annual review of .*)$");

	private static final String[] RESULTS_HEADER = new String[] { "pmid", "pmcid", "doi", "same_suggestions",
		"score", "score2", "score2_parts", "confidence", "include", "existing", "suggestion_original", "suggestion", "suggestion_processed",
		"publication_and_name_existing", "name_existing_some_publication_different", "some_publication_existing_name_different", "name_existing_publication_different",
		"name_match", "link_match", "name_word_match",
		"links_abstract", "links_fulltext", "from_abstract_link",
		"homepage", "homepage_broken", "homepage_missing", "homepage_biotools", "link", "link_biotools", "download", "download_biotools", "documentation", "documentation_biotools", "broken_links",
		"other_scores", "other_scores2", "other_scores2_parts", "other_suggestions_original", "other_suggestions", "other_suggestions_processed",
		"other_publication_and_name_existing", "other_name_existing_some_publication_different", "other_some_publication_existing_name_different", "other_name_existing_publication_different",
		"other_links_abstract", "other_links_fulltext",
		"leftover_links_abstract", "leftover_links_fulltext",
		"title", "tool_title_others", "tool_title_extracted_original", "tool_title", "tool_title_pruned", "tool_title_acronym",
		"description", "description_biotools",
		"license_homepage", "license_link", "license_download", "license_documentation", "license_abstract", "license", "license_biotools",
		"language_homepage", "language_link", "language_download", "language_documentation", "language_abstract", "language", "language_biotools",
		"oa", "journal_title", "pub_date", "citations_count", "citations_timestamp", "citations_count_normalised",
		"corresp_author_name", "credit_name_biotools", "corresp_author_orcid", "credit_orcidid_biotools", "corresp_author_email", "credit_email_biotools", "corresp_author_phone", "corresp_author_uri", "credit_url_biotools", "credit" };
	private static final String[] DIFF_HEADER = new String[] { "biotools_id", "score_score2", "current_publications", "modify_publications", "add_publications", "current_name", "modify_name", "possibly_related",
		"current_homepage", "modify_homepage", "current_links", "add_links", "current_downloads", "add_downloads", "current_documentations", "add_documentations",
		"current_license", "modify_license", "current_languages", "add_languages", "current_credits", "modify_credits", "add_credits" };
	private static final String DOCS_OUTPUT = "https://pub2tools.readthedocs.io/en/latest/output.html#";

	private static boolean isBroken(String url, Database db) {
		if (db.getWebpage(url, false) != null && !db.getWebpage(url, false).isBroken()) {
			return false;
		}
		if (db.getDoc(url, false) != null && !db.getDoc(url, false).isBroken()) {
			return false;
		}
		return true;
	}

	private static boolean creditMatch(List<Tool> biotools, int i, List<CorrespAuthor> credits) {
		List<Credit> creditsBiotools = biotools.get(i).getCredit();
		if (creditsBiotools != null) {
			for (Credit creditBiotools : creditsBiotools) {
				for (CorrespAuthor credit : credits) {
					if (creditBiotools.getName() != null && Common.creditNameEqual(credit.getName(), creditBiotools.getName())
							|| creditBiotools.getOrcidid() != null && Common.creditOrcidEqual(credit.getOrcid(), creditBiotools.getOrcidid())
							|| creditBiotools.getEmail() != null && Common.creditEmailEqual(credit.getEmail(), creditBiotools.getEmail())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static String removeLowestSubdomain(String urlTrimmed) {
		int slash = urlTrimmed.indexOf('/');
		int firstDot = urlTrimmed.indexOf('.');
		if (firstDot >= 0) {
			int secondDot = urlTrimmed.indexOf('.', firstDot + 1);
			if (secondDot >= 0) {
				if (slash < 0 || secondDot < slash) {
					return urlTrimmed.substring(firstDot + 1);
				}
			}
		}
		return urlTrimmed;
	}

	private static void linksMatch(Map<Integer, List<String>> linkMatchMap, String suggestionLink, Suggestion2 suggestion, List<List<String>> queryLinks) {
		suggestionLink = Common.trimUrl(suggestionLink);
		for (int j = 0; j < queryLinks.size(); ++j) {
			if (suggestion.getPublicationAndNameExisting() != null && suggestion.getPublicationAndNameExisting().contains(j)
					|| suggestion.getNameExistingSomePublicationDifferent() != null && suggestion.getNameExistingSomePublicationDifferent().contains(j)
					|| suggestion.getSomePublicationExistingNameDifferent() != null && suggestion.getSomePublicationExistingNameDifferent().contains(j)) {
				continue;
			}
			List<String> matchedLinks = null;
			for (String queryLink : queryLinks.get(j)) {
				if (suggestionLink.equalsIgnoreCase(queryLink)) {
					if (matchedLinks == null) {
						matchedLinks = new ArrayList<>();
					}
					matchedLinks.add(suggestionLink);
				} else {
					String suggestionLinkTrimmed = removeLowestSubdomain(suggestionLink);
					String queryLinkTrimmed = removeLowestSubdomain(queryLink);
					String rest = null;
					String matchedLink = null;
					if (suggestionLinkTrimmed.startsWith(queryLinkTrimmed)) {
						rest = suggestionLinkTrimmed.substring(queryLinkTrimmed.length());
						matchedLink = queryLinkTrimmed;
					} else if (queryLinkTrimmed.startsWith(suggestionLinkTrimmed)) {
						rest = queryLinkTrimmed.substring(suggestionLinkTrimmed.length());
						matchedLink = suggestionLinkTrimmed;
					}
					if (rest != null) {
						if (!suggestionLinkTrimmed.startsWith("GITHUB.IO") && !suggestionLinkTrimmed.startsWith("SOURCEFORGE.NET")
								&& !suggestionLinkTrimmed.startsWith("READTHEDOCS.IO") && !suggestionLinkTrimmed.startsWith("R-PROJECT.ORG")) {
							int slash = rest.indexOf('/');
							if (slash < 0 || rest.indexOf('/', slash + 1) < 0) {
								if (matchedLinks == null) {
									matchedLinks = new ArrayList<>();
								}
								matchedLinks.add(matchedLink);
							}
						}
					}
				}
			}
			if (matchedLinks != null && !matchedLinks.isEmpty()) {
				List<String> linkMatchLinks = linkMatchMap.get(j);
				if (linkMatchLinks == null) {
					linkMatchLinks = new ArrayList<>();
					linkMatchMap.put(j, linkMatchLinks);
				}
				linkMatchLinks.addAll(matchedLinks);
			}
		}
	}

	private static <T> void removeBroken(List<BiotoolsLink<T>> links, Set<BiotoolsLink<?>> broken, Database db, boolean doc, String name) {
		for (Iterator<BiotoolsLink<T>> it = links.iterator(); it.hasNext(); ) {
			BiotoolsLink<T> link = it.next();
			boolean removed = false;
			if (!doc) {
				if (db.getWebpage(link.getUrl(), true) == null || db.getWebpage(link.getUrl(), true).isBroken()) {
					broken.add(link);
					it.remove();
					removed = true;
				}
			} else {
				if (db.getDoc(link.getUrl(), true) == null || db.getDoc(link.getUrl(), true).isBroken()) {
					broken.add(link);
					it.remove();
					removed = true;
				}
			}
			if (!removed) {
				if (!Common.BIOTOOLS_SCHEMA_URLFTP_PATTERN.matcher(link.getUrl()).matches()) {
					logger.warn("Discarded invalid link url '{}' (for name '{}')", link.getUrl(), name);
					it.remove();
				}
			}
		}
	}

	private static String chooseHomepage(List<String> links, List<BiotoolsLink<LinkType>> linkLinks, List<BiotoolsLink<DocumentationType>> documentationLinks, Database db) {
		for (Iterator<BiotoolsLink<LinkType>> it = linkLinks.iterator(); it.hasNext(); ) {
			BiotoolsLink<LinkType> linkLink = it.next();
			if (linkLink.getType() == LinkType.OTHER) {
				it.remove();
				return linkLink.getUrl();
			}
		}
		for (Iterator<BiotoolsLink<LinkType>> it = linkLinks.iterator(); it.hasNext(); ) {
			BiotoolsLink<LinkType> linkLink = it.next();
			if (linkLink.getType() == LinkType.REPOSITORY) {
				it.remove();
				return linkLink.getUrl();
			}
		}
		for (Iterator<BiotoolsLink<DocumentationType>> it = documentationLinks.iterator(); it.hasNext(); ) {
			BiotoolsLink<DocumentationType> documentationLink = it.next();
			if (documentationLink.getType() == DocumentationType.GENERAL) {
				it.remove();
				return documentationLink.getUrl();
			}
		}
		for (Iterator<BiotoolsLink<DocumentationType>> it = documentationLinks.iterator(); it.hasNext(); ) {
			BiotoolsLink<DocumentationType> documentationLink = it.next();
			if ((documentationLink.getType() == DocumentationType.USER_MANUAL
					|| documentationLink.getType() == DocumentationType.INSTALLATION_INSTRUCTIONS
					|| documentationLink.getType() == DocumentationType.TRAINING_MATERIAL
					|| documentationLink.getType() == DocumentationType.API_DOCUMENTATION
					|| documentationLink.getType() == DocumentationType.FAQ
					|| documentationLink.getType() == DocumentationType.QUICK_START_GUIDE)) {
				it.remove();
				return documentationLink.getUrl();
			}
		}
		for (String link : links) {
			link = Common.prependHttp(link);
			if (db.getWebpage(link, false) != null && !db.getWebpage(link, false).isBroken() || db.getDoc(link, false) != null && !db.getDoc(link, false).isBroken()) {
				if (!Common.DOWNLOAD_EXT.matcher(link).find() && Common.BIOTOOLS_SCHEMA_URLFTP_PATTERN.matcher(link).matches()) {
					return link;
				}
			}
		}
		return null;
	}

	private static List<Integer> removeExisting(List<Integer> existing, List<Tool> biotools, List<String> toolTitleOthers, String suggestionProcessed) {
		List<Integer> removeIndex = new ArrayList<>();
		if (existing != null) {
			for (int i = 0; i < existing.size(); ++i) {
				Tool biotool = biotools.get(existing.get(i));
				String idProcessed = String.join("", biotool.getBiotoolsID());
				String nameProcessed = String.join("", biotool.getName());
				for (String other : toolTitleOthers) {
					if ((idProcessed.contains(other) || nameProcessed.contains(other)) && !(suggestionProcessed.equals(idProcessed) || suggestionProcessed.equals(nameProcessed))) {
						removeIndex.add(i);
					}
				}
			}
		}
		return removeIndex;
	}

	private static String currentHomepage(Tool biotool, Database db) {
		String homepage = biotool.getHomepage();
		if (biotool.getHomepage_status() != 0) {
			homepage += " (homepage_status: " + biotool.getHomepage_status() + ")";
		}
		Webpage webpage = db.getWebpage(biotool.getHomepage(), false);
		if (webpage != null && webpage.isBroken()) {
			homepage += " (broken)";
		}
		return homepage;
	}

	private static void writeField(Writer writer, String value, boolean last) throws IOException {
		if (value != null && value.length() > 0 && (value.charAt(0) == '"' || value.indexOf('\t') > -1)) {
			value = "\"" + value.replace("\"", "\"\"") + "\"";
		}
		if (value != null) {
			writer.write(value);
		}
		if (last) {
			writer.write("\n");
		} else {
			writer.write("\t");
		}
	}

	private static void writeField(Writer writer, String value) throws IOException {
		writeField(writer, value, false);
	}

	private static List<String> getPublicationAndNameExisting(Suggestion2 suggestion, List<Tool> biotools) {
		if (suggestion != null && suggestion.getPublicationAndNameExisting() != null) {
			return suggestion.getPublicationAndNameExisting().stream().map(e -> biotools.get(e)).map(q -> q.getBiotoolsID()).collect(Collectors.toList());
		} else {
			return null;
		}
	}

	private static List<String> getNameExistingSomePublicationDifferent(Suggestion2 suggestion, List<Tool> biotools) {
		if (suggestion != null && suggestion.getNameExistingSomePublicationDifferent() != null) {
			return IntStream.range(0, suggestion.getNameExistingSomePublicationDifferent().size())
				.mapToObj(i -> biotools.get(suggestion.getNameExistingSomePublicationDifferent().get(i)).getBiotoolsID() +
					((suggestion.getNameExistingSomePublicationDifferentPubIds() != null && suggestion.getNameExistingSomePublicationDifferentPubIds().get(i) != null) ? (" (" +
						String.join(" ; ", suggestion.getNameExistingSomePublicationDifferentPubIds().get(i).stream().map(p -> p.toString()).collect(Collectors.toList()))
					+ ")") : "")).collect(Collectors.toList());
		} else {
			return null;
		}
	}

	private static List<String> getSomePublicationExistingNameDifferent(Suggestion2 suggestion, List<Tool> biotools) {
		if (suggestion != null && suggestion.getSomePublicationExistingNameDifferent() != null) {
			return IntStream.range(0, suggestion.getSomePublicationExistingNameDifferent().size())
				.mapToObj(i -> biotools.get(suggestion.getSomePublicationExistingNameDifferent().get(i)).getBiotoolsID() + " (" + biotools.get(suggestion.getSomePublicationExistingNameDifferent().get(i)).getName() + ")" +
					((suggestion.getSomePublicationExistingNameDifferentPubIds() != null && suggestion.getSomePublicationExistingNameDifferentPubIds().get(i) != null && !suggestion.getSomePublicationExistingNameDifferentPubIds().get(i).isEmpty()) ? (" (" +
						String.join(" ; ", suggestion.getSomePublicationExistingNameDifferentPubIds().get(i).stream().map(p -> p.toString()).collect(Collectors.toList()))
					+ ")") : "")).collect(Collectors.toList());
		} else {
			return null;
		}
	}

	private static List<String> getNameExistingPublicationDifferent(Suggestion2 suggestion, List<Tool> biotools) {
		if (suggestion != null && suggestion.getNameExistingPublicationDifferent() != null) {
			return IntStream.range(0, suggestion.getNameExistingPublicationDifferent().size())
				.mapToObj(i -> biotools.get(suggestion.getNameExistingPublicationDifferent().get(i)).getBiotoolsID() +
					((suggestion.getNameExistingPublicationDifferentPubIds() != null && suggestion.getNameExistingPublicationDifferentPubIds().get(i) != null) ? (" (" +
						String.join(" ; ", suggestion.getNameExistingPublicationDifferentPubIds().get(i).stream().map(p -> p.toString()).collect(Collectors.toList()))
					+ ")") : "")).collect(Collectors.toList());
		} else {
			return null;
		}
	}

	private static List<String> getNameMatch(Result2 result, List<Tool> biotools) {
		return result.getNameMatch().stream().map(e -> biotools.get(e)).map(q -> q.getBiotoolsID() + " (" + q.getName() + ")").collect(Collectors.toList());
	}

	private static List<String> getLinkMatch(Result2 result, List<Tool> biotools) {
		return IntStream.range(0, result.getLinkMatch().size())
			.mapToObj(i -> biotools.get(result.getLinkMatch().get(i)).getBiotoolsID() + " (" +
				String.join(" ; ", result.getLinkMatchLinks().get(i))
			+ ")").collect(Collectors.toList());
	}

	private static List<String> getNameWordMatch(Result2 result, List<Tool> biotools) {
		return result.getNameWordMatch().stream().map(e -> biotools.get(e)).map(q -> q.getBiotoolsID() + " (" + q.getName() + ")").collect(Collectors.toList());
	}

	private static List<Integer> addDiffTool(Suggestion2 suggestion, boolean include, Result2 result, Database db, List<Tool> biotools, PreProcessor preProcessor, List<Diff> diffs, List<Tool> tools,
			String name, String description, String homepage, boolean homepageBroken, boolean homepageMissing, Set<BiotoolsLink<LinkType>> linkLinks, Set<BiotoolsLink<DownloadType>> downloadLinks, Set<BiotoolsLink<DocumentationType>> documentationLinks,
			Provenance bestLicense, Provenance bestAbstractLicense, List<Provenance> allLanguages, List<Provenance> abstractLanguagesUnique, List<CorrespAuthor> credits, boolean includeAll) {
		double scoreScore2 = suggestion.getScore2() < 0 ? suggestion.getScore() + 10000 : suggestion.getScore2();

		List<Integer> existing = new ArrayList<>();

		Set<Integer> possiblyRelated = new LinkedHashSet<>();
		List<Integer> nameExistingPublicationDifferentAddToDiff = new ArrayList<>();
		List<Integer> nameMatchAddToDiff = new ArrayList<>();
		if (suggestion.confident()) {
			List<Integer> linkMatchRemoveFromPossiblyRelated = new ArrayList<>();
			if (include) {
				if (suggestion.getNameExistingPublicationDifferent() != null) {
					for (int i = 0; i < suggestion.getNameExistingPublicationDifferent().size(); ++i) {
						boolean added = false;
						for (int j = 0; j < result.getLinkMatch().size(); ++j) {
							if (suggestion.getNameExistingPublicationDifferent().get(i).equals(result.getLinkMatch().get(j))) {
								nameExistingPublicationDifferentAddToDiff.add(i);
								linkMatchRemoveFromPossiblyRelated.add(j);
								added = true;
								break;
							}
						}
						if (!added) {
							if (creditMatch(biotools, suggestion.getNameExistingPublicationDifferent().get(i), credits)) {
								nameExistingPublicationDifferentAddToDiff.add(i);
							}
						}
					}
				}
				for (int i = 0; i < result.getNameMatch().size(); ++i) {
					boolean added = false;
					for (int j = 0; j < result.getLinkMatch().size(); ++j) {
						if (result.getNameMatch().get(i).equals(result.getLinkMatch().get(j))) {
							nameMatchAddToDiff.add(i);
							linkMatchRemoveFromPossiblyRelated.add(j);
							added = true;
							break;
						}
					}
					if (!added) {
						if (creditMatch(biotools, result.getNameMatch().get(i), credits)) {
							nameMatchAddToDiff.add(i);
						}
					}
				}
			}

			if (suggestion.getNameExistingPublicationDifferent() != null) {
				for (int i = 0; i < suggestion.getNameExistingPublicationDifferent().size(); ++i) {
					if (!nameExistingPublicationDifferentAddToDiff.contains(i)) {
						possiblyRelated.add(suggestion.getNameExistingPublicationDifferent().get(i));
					}
				}
			}
			for (int i = 0; i < result.getNameMatch().size(); ++i) {
				if (!nameMatchAddToDiff.contains(i)) {
					possiblyRelated.add(result.getNameMatch().get(i));
				}
			}
			List<Integer> possiblyRelatedLinkMatch = new ArrayList<>();
			for (int i = 0; i < result.getLinkMatch().size(); ++i) {
				if (!linkMatchRemoveFromPossiblyRelated.contains(i)) {
					possiblyRelatedLinkMatch.add(result.getLinkMatch().get(i));
				}
			}
			if (possiblyRelatedLinkMatch.size() <= Common.LINK_MATCH_DISPLAY_LIMIT) {
				possiblyRelated.addAll(possiblyRelatedLinkMatch);
			}
			// result.getNameWordMatch() is omitted
		}

		List<Integer> publicationAndNameExistingRemoveIndex = new ArrayList<>();
		List<Integer> nameExistingSomePublicationDifferentRemoveIndex = new ArrayList<>();
		List<Integer> somePublicationExistingNameDifferentRemoveIndex = new ArrayList<>();
		List<String> toolTitleOthers = new ArrayList<>();
		for (List<String> toolTitleOther : result.getToolTitleOthers()) {
			for (String other : toolTitleOther) {
				for (String otherPart : NOT_ALPHANUM.split(other)) {
					otherPart = Common.TOOL_TITLE_TRIM.matcher(String.join("", preProcessor.process(otherPart))).replaceFirst("");
					if (otherPart.length() > 1) {
						toolTitleOthers.add(otherPart);
						break;
					}
				}
			}
		}
		if (!toolTitleOthers.isEmpty()) {
			String suggestionProcessed = suggestion.getProcessed().replace(" ", "");
			publicationAndNameExistingRemoveIndex = removeExisting(suggestion.getPublicationAndNameExisting(), biotools, toolTitleOthers, suggestionProcessed);
			nameExistingSomePublicationDifferentRemoveIndex= removeExisting(suggestion.getNameExistingSomePublicationDifferent(), biotools, toolTitleOthers, suggestionProcessed);
			somePublicationExistingNameDifferentRemoveIndex = removeExisting(suggestion.getSomePublicationExistingNameDifferent(), biotools, toolTitleOthers, suggestionProcessed);
		}

		if (suggestion.getPublicationAndNameExisting() != null) {
			for (int i = 0; i < suggestion.getPublicationAndNameExisting().size(); ++i) {
				if (publicationAndNameExistingRemoveIndex.contains(i)) {
					continue;
				}
				if (suggestion.confident()) {
					DiffGetter.addDiff(diffs, DiffGetter.makeDiff(scoreScore2, possiblyRelated, biotools, suggestion.getPublicationAndNameExisting().get(i), result.getPubIds(), null, null, homepage, linkLinks, downloadLinks, documentationLinks, bestLicense, allLanguages, credits, db));
				} else {
					DiffGetter.addDiff(diffs, DiffGetter.makeDiff(scoreScore2, possiblyRelated, biotools, suggestion.getPublicationAndNameExisting().get(i), result.getPubIds(), null, null, null, null, null, null, bestAbstractLicense, abstractLanguagesUnique, credits, db));
				}
				existing.add(suggestion.getPublicationAndNameExisting().get(i));
			}
		}
		if (suggestion.getNameExistingSomePublicationDifferent() != null) {
			for (int i = 0; i < suggestion.getNameExistingSomePublicationDifferent().size(); ++i) {
				if (nameExistingSomePublicationDifferentRemoveIndex.contains(i)) {
					continue;
				}
				if (suggestion.confident()) {
					DiffGetter.addDiff(diffs, DiffGetter.makeDiff(scoreScore2, possiblyRelated, biotools, suggestion.getNameExistingSomePublicationDifferent().get(i), result.getPubIds(), suggestion.getNameExistingSomePublicationDifferentPubIds().get(i), null, homepage, linkLinks, downloadLinks, documentationLinks, bestLicense, allLanguages, credits, db));
				} else {
					DiffGetter.addDiff(diffs, DiffGetter.makeDiff(scoreScore2, possiblyRelated, biotools, suggestion.getNameExistingSomePublicationDifferent().get(i), result.getPubIds(), suggestion.getNameExistingSomePublicationDifferentPubIds().get(i), null, null, null, null, null, bestAbstractLicense, abstractLanguagesUnique, credits, db));
				}
				existing.add(suggestion.getNameExistingSomePublicationDifferent().get(i));
			}
		}
		if (suggestion.getSomePublicationExistingNameDifferent() != null) {
			for (int i = 0; i < suggestion.getSomePublicationExistingNameDifferent().size(); ++i) {
				if (somePublicationExistingNameDifferentRemoveIndex.contains(i)) {
					continue;
				}
				if (suggestion.confident()) {
					DiffGetter.addDiff(diffs, DiffGetter.makeDiff(scoreScore2, possiblyRelated, biotools, suggestion.getSomePublicationExistingNameDifferent().get(i), result.getPubIds(), suggestion.getSomePublicationExistingNameDifferentPubIds().get(i), name, homepage, linkLinks, downloadLinks, documentationLinks, bestLicense, allLanguages, credits, db));
				} else {
					DiffGetter.addDiff(diffs, DiffGetter.makeDiff(scoreScore2, possiblyRelated, biotools, suggestion.getSomePublicationExistingNameDifferent().get(i), result.getPubIds(), suggestion.getSomePublicationExistingNameDifferentPubIds().get(i), null, null, null, null, null, bestAbstractLicense, abstractLanguagesUnique, credits, db));
				}
				existing.add(suggestion.getSomePublicationExistingNameDifferent().get(i));
			}
		}

		for (Integer i : nameExistingPublicationDifferentAddToDiff) {
			DiffGetter.addDiff(diffs, DiffGetter.makeDiff(scoreScore2, possiblyRelated, biotools, suggestion.getNameExistingPublicationDifferent().get(i), result.getPubIds(), suggestion.getNameExistingPublicationDifferentPubIds().get(i), null, homepage, linkLinks, downloadLinks, documentationLinks, bestLicense, allLanguages, credits, db));
			existing.add(suggestion.getNameExistingPublicationDifferent().get(i));
		}
		for (Integer i : nameMatchAddToDiff) {
			DiffGetter.addDiff(diffs, DiffGetter.makeDiff(scoreScore2, possiblyRelated, biotools, result.getNameMatch().get(i), null, result.getPubIds(), name, homepage, linkLinks, downloadLinks, documentationLinks, bestLicense, allLanguages, credits, db));
			existing.add(result.getNameMatch().get(i));
		}

		if ((existing.isEmpty() && include) || includeAll) {
			Tool tool = new Tool();

			tool.setName(name);
			tool.setDescription(description);
			tool.setHomepage(suggestion.getHomepage());

			tool.setLanguage(allLanguages.stream().map(p -> p.getObject()).collect(Collectors.toList()));
			if (bestLicense != null) {
				tool.setLicense(bestLicense.getObject());
			}

			List<Link<LinkType>> links = new ArrayList<>();
			for (BiotoolsLink<LinkType> link : linkLinks) {
				Link<LinkType> newLink = new Link<>();
				newLink.setUrl(link.getUrl());
				newLink.setType(Collections.singletonList(link.getType()));
				links.add(newLink);
			}
			tool.setLink(links);

			List<LinkDownload> downloads = new ArrayList<>();
			for (BiotoolsLink<DownloadType> download : downloadLinks) {
				LinkDownload newDownload = new LinkDownload();
				newDownload.setUrl(download.getUrl());
				newDownload.setType(download.getType());
				downloads.add(newDownload);
			}
			tool.setDownload(downloads);

			List<Link<DocumentationType>> documentations = new ArrayList<>();
			for (BiotoolsLink<DocumentationType> documentation : documentationLinks) {
				Link<DocumentationType> newDocumentation = new Link<>();
				newDocumentation.setUrl(documentation.getUrl());
				newDocumentation.setType(Collections.singletonList(documentation.getType()));
				documentations.add(newDocumentation);
			}
			tool.setDocumentation(documentations);

			List<org.edamontology.edammap.core.input.json.Publication> publication = new ArrayList<>();
			for (PubIds pubIds : result.getPubIds().stream().filter(id -> !id.getPmid().isEmpty() || !id.getPmcid().isEmpty() || !id.getDoi().isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new))) {
				org.edamontology.edammap.core.input.json.Publication newPublication = new org.edamontology.edammap.core.input.json.Publication();
				if (!pubIds.getDoi().isEmpty()) {
					newPublication.setDoi(pubIds.getDoi());
				}
				if (!pubIds.getPmid().isEmpty()) {
					newPublication.setPmid(pubIds.getPmid());
				}
				if (!pubIds.getPmcid().isEmpty()) {
					newPublication.setPmcid(pubIds.getPmcid());
				}
				publication.add(newPublication);
			}
			tool.setPublication(publication);

			List<Credit> credit = new ArrayList<>();
			for (CorrespAuthor ca : credits) {
				Credit newCredit = new Credit();
				if (!ca.getName().isEmpty()) {
					newCredit.setName(ca.getName());
				}
				if (!ca.getEmail().isEmpty()) {
					newCredit.setEmail(ca.getEmail());
				}
				if (!ca.getUri().isEmpty()) {
					newCredit.setUrl(ca.getUri());
				}
				if (!ca.getOrcid().isEmpty()) {
					newCredit.setOrcidid(ca.getOrcid());
				}
				newCredit.setTypeEntity(EntityType.PERSON);
				credit.add(newCredit);
			}
			tool.setCredit(credit);

			tool.setConfidence_flag(suggestion.confidence().toString());

			if (includeAll) {
				Map<String, Object> status = new LinkedHashMap<>();
				status.put("score", suggestion.getScore());
				status.put("score2", suggestion.getScore2() > -1 ? suggestion.getScore2() : null);
				status.put("score2Parts", suggestion.getScore2() > -1 ? suggestion.getScore2Parts() : null);
				status.put("include", include);
				status.put("existing", existing.isEmpty() ? null : existing.stream().map(e -> biotools.get(e)).map(q -> q.getBiotoolsID()).collect(Collectors.toList()));
				status.put("publicationAndNameExisting", getPublicationAndNameExisting(suggestion, biotools));
				status.put("nameExistingSomePublicationDifferent", getNameExistingSomePublicationDifferent(suggestion, biotools));
				status.put("somePublicationExistingNameDifferent", getSomePublicationExistingNameDifferent(suggestion, biotools));
				status.put("nameExistingPublicationDifferent", getNameExistingPublicationDifferent(suggestion, biotools));
				List<String> nameMatch = getNameMatch(result, biotools);
				status.put("nameMatch", nameMatch.isEmpty() ? null : nameMatch);
				List<String> linkMatch = getLinkMatch(result, biotools);
				status.put("linkMatch", linkMatch.isEmpty() ? null : linkMatch);
				List<String> nameWordMatch = getNameWordMatch(result, biotools);
				status.put("nameWordMatch", nameWordMatch.isEmpty() ? null : nameWordMatch);
				status.put("homepageBroken", homepageBroken);
				status.put("homepageMissing", homepageMissing);
				List<String> otherNames = Common.getNamesOther(result, biotools, "bio.tools/");
				status.put("otherNames", otherNames.isEmpty() ? null : otherNames);
				tool.addOther(TOOL_STATUS, status);
			}

			tools.add(tool);
		}

		return existing;
	}

	private static int includeConditions(boolean homepageMissing, Provenance bestLicense, List<Provenance> allLanguages, List<PubIds> pubIds) {
		int conditions = 0;
		if (!homepageMissing) {
			++conditions;
		}
		if (bestLicense != null) {
			++conditions;
		}
		if (allLanguages != null && !allLanguages.isEmpty()) {
			++conditions;
		}
		if (pubIds != null && !pubIds.isEmpty()) {
			boolean pubIdsCondition = true;
			for (PubIds p : pubIds) {
				if ((p.getPmid() != null && !p.getPmid().isEmpty()) || (p.getPmcid() != null && !p.getPmcid().isEmpty()) || (p.getDoi() == null || p.getDoi().isEmpty())) {
					pubIdsCondition = false;
				}
			}
			if (pubIdsCondition) {
				++conditions;
			}
		}
		return conditions;
	}

	private static Pattern notPattern(String notString) {
		notString = notString.replace(".", "\\.");
		Pattern notPattern = Pattern.compile("(?i)([^\\p{L}\\p{N}]|^)"
			+ Arrays.asList(notString.split(" ")).stream().filter(s -> !s.isEmpty()).collect(Collectors.joining("[^\\p{L}\\p{N}]+")) + "([^\\p{L}\\p{N}]|$)");
		return notPattern;
	}

	private static void writeResult(Result2 result, Database db, Writer resultsWriter,
			List<Tool> biotools, List<License> licenses, List<Language> languages, List<String> languageKeywords, Scrape scrape, PreProcessor preProcessor,
			List<Diff> diffs, List<Tool> tools, List<Pattern> notAbstract, List<Pattern> notTitle, boolean includeAll) throws IOException {

		final String name;
		if (!result.getSuggestions().isEmpty()) {
			name = result.getSuggestions().get(0).getExtracted();
		} else {
			name = "";
		}

		final Suggestion2 suggestion;
		if (!result.getSuggestions().isEmpty()) {
			suggestion = result.getSuggestions().get(0);
		} else {
			suggestion = null;
		}

		final String homepage;
		boolean homepageBroken = false;
		boolean homepageMissing = true;
		if (suggestion != null) {
			homepageBroken = suggestion.isHomepageBroken();
			homepageMissing = suggestion.isHomepageMissing();
			if (!homepageBroken && !homepageMissing) {
				homepage = suggestion.getHomepage();
			} else {
				homepage = "";
			}
		} else {
			homepage = "";
		}

		Set<BiotoolsLink<LinkType>> linkLinks = new LinkedHashSet<>();
		if (suggestion != null) {
			linkLinks = suggestion.getLinkLinks();
		}

		Set<BiotoolsLink<DownloadType>> downloadLinks = new LinkedHashSet<>();
		if (suggestion != null) {
			downloadLinks = suggestion.getDownloadLinks();
		}

		Set<BiotoolsLink<DocumentationType>> documentationLinks = new LinkedHashSet<>();
		if (suggestion != null) {
			documentationLinks = suggestion.getDocumentationLinks();
		}

		List<Provenance> webpageLicenses = new ArrayList<>();

		String homepageLicense = null;
		if (!homepage.isEmpty()) {
			if (db.getWebpage(homepage, false) != null) {
				homepageLicense = db.getWebpage(homepage, false).getLicense();
			} else if (db.getDoc(homepage, false) != null) {
				homepageLicense = db.getDoc(homepage, false).getLicense();
			}
			if (homepageLicense != null && !homepageLicense.isEmpty()) {
				webpageLicenses.add(new Provenance(homepageLicense, homepage));
			}
		}

		List<Provenance> linkLicenses = linkLinks.stream().map(l -> db.getWebpage(l.getUrl(), true) != null ? new Provenance(db.getWebpage(l.getUrl(), true).getLicense(), l.getUrl()) : new Provenance()).collect(Collectors.toList());
		webpageLicenses.addAll(linkLicenses.stream().filter(p -> !p.isEmpty()).collect(Collectors.toList()));

		List<Provenance> downloadLicenses = downloadLinks.stream().map(l -> db.getWebpage(l.getUrl(), true) != null ? new Provenance(db.getWebpage(l.getUrl(), true).getLicense(), l.getUrl()) : new Provenance()).collect(Collectors.toList());
		webpageLicenses.addAll(downloadLicenses.stream().filter(p -> !p.isEmpty()).collect(Collectors.toList()));

		List<Provenance> documentationLicenses = documentationLinks.stream().map(l -> db.getDoc(l.getUrl(), true) != null ? new Provenance(db.getDoc(l.getUrl(), true).getLicense(), l.getUrl()) : new Provenance()).collect(Collectors.toList());
		webpageLicenses.addAll(documentationLicenses.stream().filter(p -> !p.isEmpty()).collect(Collectors.toList()));

		List<List<Provenance>> abstractLicenses = new ArrayList<>();
		for (int i = 0; i < result.getPubIds().size(); ++i) {
			List<String> abstractSentences = result.getAbstractSentences().get(i);
			String provenance = result.getPubIds().get(i).toString();
			abstractLicenses.add(abstractSentences.stream()
				.map(s -> new LicenseSearch(s).bestMatch(licenses, false))
				.filter(l -> l != null)
				.map(l -> new Provenance(l.getOriginal(), provenance))
				.collect(Collectors.toList()));
		}

		List<Provenance> allLicenses = new ArrayList<>();
		for (Provenance webpageLicense : webpageLicenses) {
			License l = new LicenseSearch(webpageLicense.getObject()).bestMatch(licenses, true);
			if (l != null) {
				allLicenses.add(new Provenance(l.getOriginal(), webpageLicense.getProvenances()));
			}
		}
		for (List<Provenance> abstractLicense : abstractLicenses) {
			allLicenses.addAll(abstractLicense);
		}

		Map<String, Integer> abstractLicenseCount = new HashMap<>();
		Map<String, Provenance> abstractLicenseProvenances = new HashMap<>();
		Provenance bestAbstractLicense = null;
		int bestAbstractCount = 0;
		for (List<Provenance> ls : abstractLicenses) {
			for (Provenance l : ls) {
				int count = 0;
				if (abstractLicenseCount.get(l.getObject()) != null) {
					count = abstractLicenseCount.get(l.getObject());
				}
				++count;
				abstractLicenseCount.put(l.getObject(), count);
				Provenance provenance;
				if (abstractLicenseProvenances.get(l.getObject()) != null) {
					provenance = abstractLicenseProvenances.get(l.getObject());
					provenance.addProvenances(l.getProvenances());
				} else {
					provenance = l;
					abstractLicenseProvenances.put(l.getObject(), provenance);
				}
				if (count > bestAbstractCount) {
					bestAbstractLicense = provenance;
					bestAbstractCount = count;
				}
			}
		}

		Map<String, Integer> licenseCount = new HashMap<>();
		Map<String, Provenance> licenseProvenances = new HashMap<>();
		Provenance bestLicense = null;
		int bestCount = 0;
		for (Provenance l : allLicenses) {
			int count = 0;
			if (licenseCount.get(l.getObject()) != null) {
				count = licenseCount.get(l.getObject());
			}
			++count;
			licenseCount.put(l.getObject(), count);
			Provenance provenance;
			if (licenseProvenances.get(l.getObject()) != null) {
				provenance = licenseProvenances.get(l.getObject());
				provenance.addProvenances(l.getProvenances());
			} else {
				provenance = l;
				licenseProvenances.put(l.getObject(), provenance);
			}
			if (count > bestCount) {
				bestLicense = provenance;
				bestCount = count;
			}
		}

		List<Provenance> webpageLanguages = new ArrayList<>();

		String homepageLanguage = null;
		if (!homepage.isEmpty()) {
			if (db.getWebpage(homepage, false) != null) {
				homepageLanguage = db.getWebpage(homepage, false).getLanguage();
			} else if (db.getDoc(homepage, false) != null) {
				homepageLanguage = db.getDoc(homepage, false).getLanguage();
			}
			if (homepageLanguage != null && !homepageLanguage.isEmpty()) {
				webpageLanguages.add(new Provenance(homepageLanguage, homepage));
			}
		}

		List<Provenance> linkLanguages = linkLinks.stream().map(l -> db.getWebpage(l.getUrl(), true) != null ? new Provenance(db.getWebpage(l.getUrl(), true).getLanguage(), l.getUrl()) : new Provenance()).collect(Collectors.toList());
		webpageLanguages.addAll(linkLanguages.stream().filter(p -> !p.isEmpty()).collect(Collectors.toList()));

		List<Provenance> downloadLanguages = downloadLinks.stream().map(l -> db.getWebpage(l.getUrl(), true) != null ? new Provenance(db.getWebpage(l.getUrl(), true).getLanguage(), l.getUrl()) : new Provenance()).collect(Collectors.toList());
		webpageLanguages.addAll(downloadLanguages.stream().filter(p -> !p.isEmpty()).collect(Collectors.toList()));

		List<Provenance> documentationLanguages = documentationLinks.stream().map(l -> db.getDoc(l.getUrl(), true) != null ? new Provenance(db.getDoc(l.getUrl(), true).getLanguage(), l.getUrl()) : new Provenance()).collect(Collectors.toList());
		webpageLanguages.addAll(documentationLanguages.stream().filter(p -> !p.isEmpty()).collect(Collectors.toList()));

		List<List<Provenance>> abstractLanguages = new ArrayList<>();
		for (int i = 0; i < result.getPubIds().size(); ++i) {
			List<String> abstractSentences = result.getAbstractSentences().get(i);
			String provenance = result.getPubIds().get(i).toString();
			abstractLanguages.add(abstractSentences.stream()
				.map(s -> new LanguageSearch(s).getMatches(languages, false, languageKeywords))
				.flatMap(l -> l.stream().map(s -> new Provenance(s, provenance)))
				.collect(Collectors.toList()));
		}
		List<Provenance> abstractLanguagesUnique = new ArrayList<>();
		for (List<Provenance> ls : abstractLanguages) {
			for (Provenance l : ls) {
				boolean found = false;
				for (Provenance unique : abstractLanguagesUnique) {
					if (l.getObject().equals(unique.getObject())) {
						unique.addProvenances(l.getProvenances());
						found = true;
						break;
					}
				}
				if (!found) {
					abstractLanguagesUnique.add(l);
				}
			}
		}

		webpageLanguages = webpageLanguages.stream()
			.flatMap(s -> new LanguageSearch(s.getObject()).getMatches(languages, true, languageKeywords).stream().map(l -> new Provenance(l, s.getProvenances())))
			.collect(Collectors.toList());
		List<Provenance> allLanguages = new ArrayList<>();
		for (Provenance l : webpageLanguages) {
			boolean found = false;
			for (Provenance all : allLanguages) {
				if (l.getObject().equals(all.getObject())) {
					all.addProvenances(l.getProvenances());
					found = true;
					break;
				}
			}
			if (!found) {
				allLanguages.add(l);
			}
		}
		for (List<Provenance> ls : abstractLanguages) {
			for (Provenance l : ls) {
				boolean found = false;
				for (Provenance all : allLanguages) {
					if (l.getObject().equals(all.getObject())) {
						all.addProvenances(l.getProvenances());
						found = true;
						break;
					}
				}
				if (!found) {
					allLanguages.add(l);
				}
			}
		}

		List<CorrespAuthor> credits = new ArrayList<>();
		for (List<CorrespAuthor> correspAuthor : result.getCorrespAuthor()) {
			for (CorrespAuthor ca : correspAuthor) {
				boolean exist = false;
				for (CorrespAuthor credit : credits) {
					if (Common.creditNameEqual(ca.getName(), credit.getName()) || Common.creditOrcidEqual(ca.getOrcid(), credit.getOrcid()) || Common.creditEmailEqual(ca.getEmail(), credit.getEmail())) {
						if (credit.getName().isEmpty()) {
							credit.setName(ca.getName());
						}
						if (credit.getOrcid().isEmpty()) {
							credit.setOrcid(ca.getOrcid());
						}
						if (credit.getEmail().isEmpty()) {
							credit.setEmail(ca.getEmail());
						}
						if (credit.getUri().isEmpty()) {
							credit.setUri(ca.getUri());
						}
						exist = true;
						break;
					}
				}
				if (!exist) {
					CorrespAuthor credit = new CorrespAuthor();
					credit.setName(ca.getName());
					credit.setOrcid(ca.getOrcid());
					credit.setEmail(ca.getEmail());
					credit.setUri(ca.getUri());
					credits.add(credit);
				}
			}
		}

		boolean include = false;
		if (suggestion != null) {
			Confidence confidence = suggestion.confidence();
			switch (confidence) {
			case high:
				include = true;
				break;
			case medium:
				if (suggestion.getScore() >= 24) {
					include = true;
				} else if (suggestion.getScore() > 12) {
					if (includeConditions(homepageMissing, bestLicense, allLanguages, null) >= 1) {
						include = true;
					}
				} else {
					if (includeConditions(homepageMissing, bestLicense, allLanguages, result.getPubIds()) >= 2) {
						include = true;
					}
				}
				break;
			case low:
				if (suggestion.getScore() >= 144) {
					include = true;
				} else if (suggestion.getScore() > 24) {
					if (includeConditions(homepageMissing, bestLicense, allLanguages, null) >= 1) {
						include = true;
					}
				} else {
					if (includeConditions(homepageMissing, bestLicense, allLanguages, result.getPubIds()) >= 2) {
						include = true;
					}
				}
				break;
			case very_low:
				if (suggestion.getScore() >= 288) {
					include = true;
				} else if (suggestion.getScore() >= 144) {
					if (includeConditions(homepageMissing, bestLicense, allLanguages, null) >= 1) {
						include = true;
					}
				} else {
					if (includeConditions(homepageMissing, bestLicense, allLanguages, result.getPubIds()) >= 2) {
						include = true;
					}
				}
				break;
			}
		}

		if (include) {
			if (HOMEPAGE_EXCLUDE.matcher(suggestion.getHomepage()).find()) {
				include = false;
			}
		}
		if (include) {
			for (String journalTitle : result.getJournalTitle()) {
				if (JOURNAL_EXCLUDE.matcher(journalTitle).find()) {
					include = false;
					break;
				}
			}
		}
		if (include) {
			for (Pattern not : notAbstract) {
				for (List<String> abstractSentences : result.getAbstractSentences()) {
					for (String abstractSentence : abstractSentences) {
						if (not.matcher(abstractSentence).find()) {
							include = false;
							break;
						}
					}
				}
				if (!include) {
					break;
				}
			}
		}
		if (include) {
			for (Pattern not : notTitle) {
				for (String title : result.getTitle()) {
					if (not.matcher(title).find()) {
						include = false;
						break;
					}
				}
				if (!include) {
					break;
				}
			}
		}

		final String description = DescriptionGetter.get(suggestion, include, homepageBroken, homepageMissing, biotools, result, homepage, linkLinks, documentationLinks, downloadLinks, db, scrape, name, preProcessor);

		List<Integer> existing = new ArrayList<>();
		if (suggestion != null) {
			existing = addDiffTool(suggestion, include, result, db, biotools, preProcessor, diffs, tools,
					name, description, homepage, homepageBroken, homepageMissing, linkLinks, downloadLinks, documentationLinks,
					bestLicense, bestAbstractLicense, allLanguages, abstractLanguagesUnique, credits, includeAll);
		}

		// Start writing from here

		writeField(resultsWriter, result.getPubIds().stream().map(p -> p.getPmid()).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getPubIds().stream().map(p -> p.getPmcid()).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getPubIds().stream().map(p -> p.getDoi()).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, result.getSameSuggestions().stream().map(pubIds -> pubIds.toString()).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, suggestion != null ? String.valueOf(suggestion.getScore()) : null);
		writeField(resultsWriter, suggestion != null && suggestion.getScore2() > -1 ? String.valueOf(suggestion.getScore2()) : null);
		writeField(resultsWriter, suggestion != null && suggestion.getScore2() > -1 ? Arrays.toString(suggestion.getScore2Parts()) : null);

		writeField(resultsWriter, suggestion != null ? suggestion.confidence().toString() : null);
		writeField(resultsWriter, String.valueOf(include));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(q -> q.getBiotoolsID()).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, suggestion != null ? suggestion.getOriginal() : null);
		writeField(resultsWriter, name);
		writeField(resultsWriter, suggestion != null ? suggestion.getProcessed() : null);

		List<String> publicationAndNameExisting = getPublicationAndNameExisting(suggestion, biotools);
		writeField(resultsWriter, publicationAndNameExisting != null ? String.join(" | ", publicationAndNameExisting) : null);

		List<String> nameExistingSomePublicationDifferent = getNameExistingSomePublicationDifferent(suggestion, biotools);
		writeField(resultsWriter, nameExistingSomePublicationDifferent != null ? String.join(" | ", nameExistingSomePublicationDifferent) : null);

		List<String> somePublicationExistingNameDifferent = getSomePublicationExistingNameDifferent(suggestion, biotools);
		writeField(resultsWriter, somePublicationExistingNameDifferent != null ? String.join(" | ", somePublicationExistingNameDifferent) : null);

		List<String> nameExistingPublicationDifferent = getNameExistingPublicationDifferent(suggestion, biotools);
		writeField(resultsWriter, nameExistingPublicationDifferent != null ? String.join(" | ", nameExistingPublicationDifferent) : null);

		List<String> nameMatch = getNameMatch(result, biotools);
		writeField(resultsWriter, String.join(" | ", nameMatch));

		List<String> linkMatch = getLinkMatch(result, biotools);
		writeField(resultsWriter, String.join(" | ", linkMatch));

		List<String> nameWordMatch = getNameWordMatch(result, biotools);
		writeField(resultsWriter, String.join(" | ", nameWordMatch));

		List<String> linksAbstract = new ArrayList<>();
		if (suggestion != null) {
			linksAbstract = suggestion.getLinksAbstract();
			writeField(resultsWriter, String.join(" | ", linksAbstract));
		} else {
			writeField(resultsWriter, null);
		}
		if (suggestion != null) {
			writeField(resultsWriter, String.join(" | ", suggestion.getLinksFulltext()));
		} else {
			writeField(resultsWriter, null);
		}

		writeField(resultsWriter, result.getSuggestions().stream().map(s -> String.valueOf(s.isFromAbstractLink())).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, suggestion != null ? suggestion.getHomepage() : null);
		writeField(resultsWriter, String.valueOf(homepageBroken));
		writeField(resultsWriter, String.valueOf(homepageMissing));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(q -> currentHomepage(q, db)).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, linkLinks.stream().map(l -> l.getUrl() + " (" + l.getType() + ")").collect(Collectors.joining(" | ")));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(t -> (t.getLink() == null ? "" : t.getLink().stream().map(l -> l.getUrl() + " (" + l.toStringType() + ")").collect(Collectors.joining(" ; ")))).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, downloadLinks.stream().map(l -> l.getUrl() + " (" + l.getType() + ")").collect(Collectors.joining(" | ")));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(t -> (t.getDownload() == null ? "" : t.getDownload().stream().map(l -> l.getUrl() + " (" + l.getType() + ")").collect(Collectors.joining(" ; ")))).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, documentationLinks.stream().map(l -> l.getUrl() + " (" + l.getType() + ")").collect(Collectors.joining(" | ")));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(t -> (t.getDocumentation() == null ? "" : t.getDocumentation().stream().map(l -> l.getUrl() + " (" + l.toStringType() + ")").collect(Collectors.joining(" ; ")))).collect(Collectors.joining(" | ")));

		if (suggestion != null) {
			writeField(resultsWriter, suggestion.getBrokenLinks().stream().map(l -> l.getUrl() + " (" + l.getType() + ")").collect(Collectors.joining(" | ")));
		} else {
			writeField(resultsWriter, null);
		}

		writeField(resultsWriter, result.getSuggestions().stream().skip(1).map(s -> String.format(Locale.ROOT, "%.1f", s.getScore())).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getSuggestions().stream().skip(1).map(s -> (s.getScore2() > -1) ? String.format(Locale.ROOT, "%.1f", s.getScore2()) : "").collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getSuggestions().stream().skip(1).map(s -> (s.getScore2() > -1) ? ("[" + Arrays.asList(s.getScore2Parts()).stream().map(part -> String.format(Locale.ROOT, "%.1f", part)).collect(Collectors.joining(", ")) + "]") : "").collect(Collectors.joining(" | ")));

		writeField(resultsWriter, result.getSuggestions().stream().skip(1).map(s -> s.getOriginal()).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getSuggestions().stream().skip(1).map(s -> s.getExtracted()).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getSuggestions().stream().skip(1).map(s -> s.getProcessed()).collect(Collectors.joining(" | ")));

		List<List<String>> otherPublicationAndNameExisting = result.getSuggestions().stream().skip(1).map(s -> s.getPublicationAndNameExisting() != null ? s.getPublicationAndNameExisting().stream().map(e -> biotools.get(e)).map(q -> q.getBiotoolsID()).collect(Collectors.toList()) : null).collect(Collectors.toList());
		writeField(resultsWriter, otherPublicationAndNameExisting.stream().map(e -> e != null ? String.join(" ; ", e) : "").collect(Collectors.joining(" | ")));

		List<List<String>> otherNameExistingSomePublicationDifferent = result.getSuggestions().stream().skip(1)
			.map(s -> s.getNameExistingSomePublicationDifferent() != null ? IntStream.range(0, s.getNameExistingSomePublicationDifferent().size())
				.mapToObj(i -> biotools.get(s.getNameExistingSomePublicationDifferent().get(i)).getBiotoolsID() +
					((s.getNameExistingSomePublicationDifferentPubIds() != null && s.getNameExistingSomePublicationDifferentPubIds().get(i) != null) ? (" (" +
						String.join(" ; ", s.getNameExistingSomePublicationDifferentPubIds().get(i).stream().map(p -> p.toString()).collect(Collectors.toList()))
					+ ")") : "")).collect(Collectors.toList())
			: null).collect(Collectors.toList());
		writeField(resultsWriter, otherNameExistingSomePublicationDifferent.stream().map(e -> e != null ? String.join(" ; ", e) : "").collect(Collectors.joining(" | ")));

		List<List<String>> otherSomePublicationExistingNameDifferent = result.getSuggestions().stream().skip(1)
			.map(s -> s.getSomePublicationExistingNameDifferent() != null ? IntStream.range(0, s.getSomePublicationExistingNameDifferent().size())
				.mapToObj(i -> biotools.get(s.getSomePublicationExistingNameDifferent().get(i)).getBiotoolsID() + " (" + biotools.get(s.getSomePublicationExistingNameDifferent().get(i)).getName() + ")" +
					((s.getSomePublicationExistingNameDifferentPubIds() != null && s.getSomePublicationExistingNameDifferentPubIds().get(i) != null && !s.getSomePublicationExistingNameDifferentPubIds().get(i).isEmpty()) ? (" (" +
						String.join(" ; ", s.getSomePublicationExistingNameDifferentPubIds().get(i).stream().map(p -> p.toString()).collect(Collectors.toList()))
					+ ")") : "")).collect(Collectors.toList())
			: null).collect(Collectors.toList());
		writeField(resultsWriter, otherSomePublicationExistingNameDifferent.stream().map(e -> e != null ? String.join(" ; ", e) : "").collect(Collectors.joining(" | ")));

		List<List<String>> otherNameExistingPublicationDifferent = result.getSuggestions().stream().skip(1)
			.map(s -> s.getNameExistingPublicationDifferent() != null ? IntStream.range(0, s.getNameExistingPublicationDifferent().size())
				.mapToObj(i -> biotools.get(s.getNameExistingPublicationDifferent().get(i)).getBiotoolsID() +
					((s.getNameExistingPublicationDifferentPubIds() != null && s.getNameExistingPublicationDifferentPubIds().get(i) != null) ? (" (" +
						String.join(" ; ", s.getNameExistingPublicationDifferentPubIds().get(i).stream().map(p -> p.toString()).collect(Collectors.toList()))
					+ ")") : "")).collect(Collectors.toList())
			: null).collect(Collectors.toList());
		writeField(resultsWriter, otherNameExistingPublicationDifferent.stream().map(e -> e != null ? String.join(" ; ", e) : "").collect(Collectors.joining(" | ")));

		List<String> otherLinksAbstract = new ArrayList<>();
		boolean otherLinksAbstractEmpty = true;
		for (int i = 1; i < result.getSuggestions().size(); ++i) {
			otherLinksAbstract.add(result.getSuggestions().get(i).getLinksAbstract().stream().collect(Collectors.joining(" ; ")));
			if (!result.getSuggestions().get(i).getLinksAbstract().isEmpty()) {
				otherLinksAbstractEmpty = false;
			}
		}
		writeField(resultsWriter, !otherLinksAbstractEmpty ? String.join(" | ", otherLinksAbstract) : null);

		List<String> otherLinksFulltext = new ArrayList<>();
		boolean otherLinksFulltextEmpty = true;
		for (int i = 1; i < result.getSuggestions().size(); ++i) {
			otherLinksFulltext.add(result.getSuggestions().get(i).getLinksFulltext().stream().collect(Collectors.joining(" ; ")));
			if (!result.getSuggestions().get(i).getLinksFulltext().isEmpty()) {
				otherLinksFulltextEmpty = false;
			}
		}
		writeField(resultsWriter, !otherLinksFulltextEmpty ? String.join(" | ", otherLinksFulltext) : null);

		writeField(resultsWriter, result.getLeftoverLinksAbstract().stream().map(l -> String.join(" ; ", l)).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getLeftoverLinksFulltext().stream().map(l -> String.join(" ; ", l)).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, String.join(" | ", result.getTitle()));
		writeField(resultsWriter, result.getToolTitleOthers().stream().map(t -> String.join(" ; ", t)).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, String.join(" | ", result.getToolTitleExtractedOriginal()));
		writeField(resultsWriter, IntStream.range(0, result.getToolTitle().size())
			.mapToObj(i -> result.getToolTitle().get(i).equals(result.getToolTitleExtractedOriginal().get(i)) ? "" : result.getToolTitle().get(i)).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, IntStream.range(0, result.getToolTitlePruned().size())
			.mapToObj(i -> result.getToolTitlePruned().get(i).equals(result.getToolTitle().get(i)) ? "" : result.getToolTitlePruned().get(i)).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, String.join(" | ", result.getToolTitleAcronym()));

		writeField(resultsWriter, description.replaceAll("\n", "\\\\n"));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(q -> q.getDescription().replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r").replaceAll("\t", "\\\\t")).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, homepageLicense);
		writeField(resultsWriter, linkLicenses.stream().map(p -> p.toString()).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, downloadLicenses.stream().map(p -> p.toString()).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, documentationLicenses.stream().map(p -> p.toString()).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, abstractLicenses.stream().map(lp -> lp.stream().map(p -> p.toString()).collect(Collectors.joining(" ; "))).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, bestLicense != null ? bestLicense.toString() : null);
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(t -> (t.getLicense() == null ? "" : t.getLicense())).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, homepageLanguage);
		writeField(resultsWriter, linkLanguages.stream().map(p -> p.toString()).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, downloadLanguages.stream().map(p -> p.toString()).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, documentationLanguages.stream().map(p -> p.toString()).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, abstractLanguages.stream().map(lp -> lp.stream().map(p -> p.toString()).collect(Collectors.joining(" ; "))).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, allLanguages.stream().map(p -> p.toString()).collect(Collectors.joining(" ; ")));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(t -> (t.getLanguage() == null ? "" : String.join(" ; ", t.getLanguage()))).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, result.isOa().stream().map(b -> String.valueOf(b)).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, String.join(" | ", result.getJournalTitle()));
		writeField(resultsWriter, IntStream.range(0, result.getPubDate().size()).mapToObj(i -> result.getPubDateHuman().get(i) + " (" + result.getPubDate().get(i) + ")").collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getCitationsCount().stream().map(i -> String.valueOf(i)).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, IntStream.range(0, result.getCitationsTimestamp().size()).mapToObj(i -> result.getCitationsTimestampHuman().get(i) + " (" + result.getCitationsTimestamp().get(i) + ")").collect(Collectors.joining(" | ")));
		writeField(resultsWriter, IntStream.range(0, result.getCitationsCount().size())
			.mapToObj(i -> (result.getCitationsCount().get(i) > -1 && result.getCitationsTimestamp().get(i) > -1 && result.getPubDate().get(i) > -1) ?
				(result.getCitationsCount().get(i) / (double) (result.getCitationsTimestamp().get(i) - result.getPubDate().get(i)) * 1000000000) : -1)
			.map(f -> String.valueOf(f))
			.collect(Collectors.joining(" | ")));

		writeField(resultsWriter, result.getCorrespAuthor().stream().map(p -> p.stream().map(ca -> ca.getName()).collect(Collectors.joining(" ; "))).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(t -> (t.getCredit() == null ? "" : t.getCredit().stream().map(c -> (c.getName() == null ? "" : c.getName())).collect(Collectors.joining(" ; ")))).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getCorrespAuthor().stream().map(p -> p.stream().map(ca -> ca.getOrcid()).collect(Collectors.joining(" ; "))).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(t -> (t.getCredit() == null ? "" : t.getCredit().stream().map(c -> (c.getOrcidid() == null ? "" : c.getOrcidid())).collect(Collectors.joining(" ; ")))).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getCorrespAuthor().stream().map(p -> p.stream().map(ca -> ca.getEmail()).collect(Collectors.joining(" ; "))).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(t -> (t.getCredit() == null ? "" : t.getCredit().stream().map(c -> (c.getEmail() == null ? "" : c.getEmail())).collect(Collectors.joining(" ; ")))).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getCorrespAuthor().stream().map(p -> p.stream().map(ca -> ca.getPhone()).collect(Collectors.joining(" ; "))).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, result.getCorrespAuthor().stream().map(p -> p.stream().map(ca -> ca.getUri()).collect(Collectors.joining(" ; "))).collect(Collectors.joining(" | ")));
		writeField(resultsWriter, existing.stream().map(e -> biotools.get(e)).map(t -> (t.getCredit() == null ? "" : t.getCredit().stream().map(c -> (c.getUrl() == null ? "" : c.getUrl())).collect(Collectors.joining(" ; ")))).collect(Collectors.joining(" | ")));

		writeField(resultsWriter, credits.stream().map(ca -> ca.toString()).collect(Collectors.joining(" | ")), true);
	}

	@SuppressWarnings("unchecked")
	public static List<Tool> run(Path outputPath, PreProcessor preProcessor, FetcherArgs fetcherArgs, String logPrefix, Idf idfProvided, List<Tool> biotoolsProvided, Database dbProvided, boolean includeAll, List<Publication> publicationsProvided, String nameProvided, List<String> webpageUrlsProvided) throws IOException, ParseException {
		Marker mainMarker = MarkerManager.getMarker(Common.MAIN_MARKER);

		List<String> license = PubFetcher.getResource(Pass2.class, "pass2/license.txt");
		List<License> licenses = license.stream().map(l -> new License(l)).collect(Collectors.toList());
		List<String> language = PubFetcher.getResource(Pass2.class, "pass2/language.txt");
		List<String> languageKeywords = PubFetcher.getResource(Pass2.class, "pass2/language_keywords.txt");
		List<Language> languages = language.stream().map(l -> new Language(l)).collect(Collectors.toList());

		List<Pattern> notAbstract = PubFetcher.getResource(SelectPub.class, "select/not_abstract.txt").stream().map(s -> notPattern(s)).collect(Collectors.toList());
		List<Pattern> notTitle = PubFetcher.getResource(SelectPub.class, "select/not_title.txt").stream().map(s -> notPattern(s)).collect(Collectors.toList());

		Scrape scrape = new Scrape(fetcherArgs.getPrivateArgs().getJournalsYaml(), fetcherArgs.getPrivateArgs().getWebpagesYaml());

		Idf idf;
		if (idfProvided != null) {
			idf = idfProvided;
		} else {
			String idfFile = outputPath.resolve(Common.IDF_FILE).toString();
			logger.info(mainMarker, "{}Loading IDF from {}", logPrefix, idfFile);
			idf = new Idf(idfFile);
		}

		List<Tool> biotools;
		if (biotoolsProvided != null) {
			biotools = biotoolsProvided;
		} else {
			String biotoolsFile = outputPath.resolve(Common.BIOTOOLS_FILE).toString();
			logger.info(mainMarker, "{}Loading all bio.tools content from {}", logPrefix, biotoolsFile);
			biotools = (List<Tool>) Json.load(biotoolsFile, QueryType.biotools, fetcherArgs.getTimeout(), fetcherArgs.getPrivateArgs().getUserAgent());
		}

		Path pass1Path = outputPath.resolve(Common.PASS1_FILE);
		logger.info(mainMarker, "{}Loading pass1 results from {}", logPrefix, pass1Path.toString());
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.CLOSE_CLOSEABLE);
		List<Result1> results1 = mapper.readValue(pass1Path.toFile(), new TypeReference<List<Result1>>() {});

		Path resultsPath = PubFetcher.outputPath(outputPath.resolve(Common.RESULTS_FILE).toString());
		Path diffPath = PubFetcher.outputPath(outputPath.resolve(Common.DIFF_FILE).toString());
		Path newPath = PubFetcher.outputPath(outputPath.resolve(Common.NEW_FILE).toString());

		List<Result2> results = new ArrayList<>();
		for (Result1 result1 : results1) {
			results.add(new Result2(result1));
		}

		logger.info(mainMarker, "{}Making pass2 results from {} pass1 results", logPrefix, results1.size());

		CharsetEncoder resultsEncoder = StandardCharsets.UTF_8.newEncoder();
		resultsEncoder.onMalformedInput(CodingErrorAction.REPLACE);
		resultsEncoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

		CharsetEncoder diffEncoder = StandardCharsets.UTF_8.newEncoder();
		diffEncoder.onMalformedInput(CodingErrorAction.REPLACE);
		diffEncoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

		CharsetEncoder newEncoder = StandardCharsets.UTF_8.newEncoder();
		newEncoder.onMalformedInput(CodingErrorAction.REPLACE);
		newEncoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

		Database db = (dbProvided != null ? dbProvided : new Database(outputPath.resolve(Common.DB_FILE).toString()));
		try (BufferedWriter resultsWriter = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(resultsPath), resultsEncoder));
				BufferedWriter diffWriter = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(diffPath), diffEncoder));
				BufferedWriter newWriter = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(newPath), newEncoder))) {

			resultsWriter.write(Arrays.stream(RESULTS_HEADER).collect(Collectors.joining("\t")) + "\n");
			resultsWriter.write(Arrays.stream(RESULTS_HEADER).map(s -> DOCS_OUTPUT + s.replaceAll("_", "-")).collect(Collectors.joining("\t")) + "\n");
			diffWriter.write(Arrays.stream(DIFF_HEADER).collect(Collectors.joining("\t")) + "\n");
			diffWriter.write(Arrays.stream(DIFF_HEADER).map(s -> DOCS_OUTPUT + s.replaceAll("_", "-")).collect(Collectors.joining("\t")) + "\n");

			logger.info(mainMarker, "{}Calculating score2 for relevant results", logPrefix);
			for (Result2 result : results) {
				if (!result.getSuggestions().isEmpty() && result.getSuggestions().get(0).calculateScore2()) {
					result.getSuggestions().get(0).setScore2(result.getSuggestions().get(0).getScore());

					double firstScore = result.getSuggestions().get(0).getScore();
					for (int i = 0; i < result.getSuggestions().size(); ++i) {
						Suggestion2 suggestion = result.getSuggestions().get(i);
						boolean increased = false;
						for (String url : suggestion.getLinksAbstract()) {
							if (!isBroken(url, db)) {
								if (suggestion.getScore2() < 0) {
									suggestion.setScore2(suggestion.getScore());
								}
								double score2Part = 0;
								if (suggestion.isFromAbstractLink()) {
									if (i == 0 || suggestion.getScore() == firstScore) {
										score2Part = FROM_ABSTRACT_LINK_INCREASE;
									} else {
										score2Part = FROM_ABSTRACT_LINK_INCREASE / NOT_FIRST_SUGGESTION_LINK_DIVIDER;
									}
								} else {
									if (i == 0 || suggestion.getScore() == firstScore) {
										score2Part = ABSTRACT_LINK_INCREASE;
									} else {
										score2Part = ABSTRACT_LINK_INCREASE / NOT_FIRST_SUGGESTION_LINK_DIVIDER;
									}
								}
								suggestion.getScore2Parts()[0] = score2Part;
								suggestion.setScore2(suggestion.getScore2() + score2Part);
								increased = true;
								break;
							}
						}
						if (!increased) {
							for (String url : suggestion.getLinksFulltext()) {
								if (!isBroken(url, db)) {
									if (suggestion.getScore2() < 0) {
										suggestion.setScore2(suggestion.getScore());
									}
									double score2Part = 0;
									if (i == 0 || suggestion.getScore() == firstScore) {
										score2Part = FULLTEXT_LINK_INCREASE;
									} else {
										score2Part = FULLTEXT_LINK_INCREASE / NOT_FIRST_SUGGESTION_LINK_DIVIDER;
									}
									suggestion.getScore2Parts()[0] = score2Part;
									suggestion.setScore2(suggestion.getScore2() + score2Part);
									break;
								}
							}
						}
					}

					if (result.getSuggestions().size() > 0) {
						List<String> toolTitlePrunedProcessed = preProcessor.process(result.getToolTitlePruned().get(0));
						String toolTitlePrunedProcessedString = String.join(" ", toolTitlePrunedProcessed);
						String toolTitleAcronymProcessedString = String.join(" ", preProcessor.process(result.getToolTitleAcronym().get(0)));
						String toolTitleExtractedOriginal = result.getToolTitleExtractedOriginal().get(0);
						int toolTitleExtractedOriginalSize = toolTitleExtractedOriginal.split(" ").length;
						for (Suggestion2 suggestion : result.getSuggestions()) {
							List<String> suggestionExtracted = new ArrayList<>(Arrays.asList(suggestion.getExtracted().split(" ")));
							String suggestionPrunedProcessed = String.join(" ", preProcessor.process(Common.toolTitlePrune(suggestionExtracted)));
							int match = 0;
							if (suggestionPrunedProcessed.length() > 2) {
								if (toolTitlePrunedProcessedString.equals(suggestionPrunedProcessed)) {
									match = toolTitlePrunedProcessed.size();
								} else if (toolTitleAcronymProcessedString.equals(suggestionPrunedProcessed)) {
									match = 1;
								} else if (!toolTitlePrunedProcessedString.isEmpty() && Common.isAcronym(toolTitlePrunedProcessedString, suggestion.getExtracted(), false)
										|| !toolTitleAcronymProcessedString.isEmpty() && Common.isAcronym(toolTitleAcronymProcessedString, suggestion.getExtracted(), false)
										|| !toolTitleExtractedOriginal.isEmpty() && toolTitleExtractedOriginalSize <= TOOL_TITLE_ORIGINAL_MAX_SIZE_FOR_ACRONYM && Common.isAcronym(suggestionPrunedProcessed, toolTitleExtractedOriginal, false)) {
									match = 2;
								} else if (toolTitlePrunedProcessedString.contains(suggestionPrunedProcessed)) {
									match = toolTitlePrunedProcessed.size() + 1;
								}
							} else if (suggestionPrunedProcessed.length() > 0) {
								if (toolTitlePrunedProcessedString.equals(suggestionPrunedProcessed)
										|| toolTitleAcronymProcessedString.equals(suggestionPrunedProcessed)) {
									match = 1;
								} else if (!toolTitleExtractedOriginal.isEmpty() && toolTitleExtractedOriginalSize <= TOOL_TITLE_ORIGINAL_MAX_SIZE_FOR_ACRONYM && Common.isAcronym(suggestionPrunedProcessed, toolTitleExtractedOriginal, false)) {
									match = 2;
								}
							}
							if (match > 0 && match < 6) {
								if (suggestion.getScore2() < 0) {
									suggestion.setScore2(suggestion.getScore());
								}
								double score2Part = 0;
								if (match == 1) {
									score2Part = TOOL_TITLE_1_INCREASE;
								} else if (match == 2) {
									score2Part = TOOL_TITLE_2_INCREASE;
								} else if (match == 3) {
									score2Part = TOOL_TITLE_3_INCREASE;
								} else if (match == 4) {
									score2Part = TOOL_TITLE_4_INCREASE;
								} else if (match == 5) {
									score2Part = TOOL_TITLE_5_INCREASE;
								}
								suggestion.getScore2Parts()[1] = score2Part;
								suggestion.setScore2(suggestion.getScore2() + score2Part);
							}
						}
					}

					for (Suggestion2 suggestion : result.getSuggestions()) {
						if (suggestion.getScore2() < 0) {
							continue;
						}
						String suggestionExtractedString = suggestion.getExtracted();
						if (suggestion.isFromAbstractLink()) {
							suggestionExtractedString = Common.PATH_SPLIT.matcher(suggestionExtractedString).replaceAll(" ").trim();
						}
						List<String> suggestionExtracted = new ArrayList<>(Arrays.asList(suggestionExtractedString.split(" ")));
						String[] suggestionPruned = Common.toolTitlePrune(suggestionExtracted).split(" ");
						double min = -1;
						for (String suggestionPart : suggestionPruned) {
							Matcher removeHyphen = CASE_REMOVE_HYPHEN.matcher(suggestionPart);
							if (removeHyphen.matches()) {
								suggestionPart = removeHyphen.group(1) + removeHyphen.group(2).toLowerCase(Locale.ROOT) + removeHyphen.group(3);
							}
							suggestionPart = suggestionPart.replaceAll("-", "");
							Matcher removePlural = CASE_REMOVE_PLURAL.matcher(suggestionPart);
							if (removePlural.matches()) {
								suggestionPart = removePlural.group(1);
							}
							if (CASE_LOWERCASE.matcher(suggestionPart).matches()) {
								if (CASE_LOWERCASE_INCREASE < min || min < 0) {
									min = CASE_LOWERCASE_INCREASE;
								}
							} else if (CASE_FIRST_CAPITAL.matcher(suggestionPart).matches()) {
								if (CASE_FIRST_CAPITAL_INCREASE < min || min < 0) {
									min = CASE_FIRST_CAPITAL_INCREASE;
								}
							} else if (CASE_UPPERCASE.matcher(suggestionPart).matches()) {
								if (CASE_UPPERCASE_INCREASE < min || min < 0) {
									min = CASE_UPPERCASE_INCREASE;
								}
							} else if (CASE_MIXED_AS_REST.matcher(suggestionPart).matches()) {
								if (CASE_MIXED_INCREASE < min || min < 0) {
									min = CASE_MIXED_INCREASE;
								}
							}
						}
						min -= (suggestionPruned.length - 1) * CASE_DECREASE;
						if (min > 0) {
							double score2Part = min;
							suggestion.getScore2Parts()[2] = score2Part;
							suggestion.setScore2(suggestion.getScore2() + score2Part);
						}
					}

					for (Suggestion2 suggestion : result.getSuggestions()) {
						if (suggestion.getScore2() < 0) {
							continue;
						}
						double sum = 0;
						String[] suggestionProcessed = suggestion.getProcessed().split(" ");
						for (String suggestionPart : suggestionProcessed) {
							sum += Math.pow(idf.getIdfShifted(suggestionPart, 0), IDF_POWER) * IDF_MULTIPLIER;
						}
						if (sum > 0) {
							double score2Part = sum / suggestionProcessed.length;
							suggestion.getScore2Parts()[3] = score2Part;
							suggestion.setScore2(suggestion.getScore2() + score2Part);
						}
					}

					for (Suggestion2 suggestion : result.getSuggestions()) {
						if (suggestion.getScore2() < 0) {
							continue;
						}
						if (suggestion.getScore2Parts()[2] == CASE_MIXED_INCREASE && suggestion.getScore() >= SCORE_MIN_FOR_MIXED_IDF_INCREASE || suggestion.getScore2Parts()[2] == CASE_UPPERCASE_INCREASE && suggestion.getScore() >= SCORE_MIN_FOR_UPPERCASE_IDF_INCREASE) {
							double score2Part = suggestion.getScore2Parts()[3];
							if (suggestion.getScore2Parts()[2] == CASE_MIXED_INCREASE && suggestion.getScore() >= SCORE_MIN_FOR_MIXED_IDF_INCREASE2) {
								score2Part += suggestion.getScore2Parts()[3] / 2;
							}
							suggestion.getScore2Parts()[2] += score2Part;
							suggestion.setScore2(suggestion.getScore2() + score2Part);
						}
					}

					Collections.sort(result.getSuggestions());
				}
			}

			logger.info(mainMarker, "{}Resorting results", logPrefix);
			Collections.sort(results);

			logger.info(mainMarker, "{}Merging results based on same suggestions", logPrefix);
			TreeSet<Integer> removeResult = new TreeSet<>();
			Set<String> publicationsProvidedMerged = new HashSet<>();
			for (int i = 0; i < results.size() - 1; ++i) {
				if (removeResult.contains(i)) {
					continue;
				}
				Result2 resultI = results.get(i);
				boolean publicationsProvidedMerge = (publicationsProvided != null && i == 0);
				if (publicationsProvidedMerge) {
					PubIds pubIds = resultI.getPubIds().get(0);
					for (Publication publicationProvided : publicationsProvided) {
						if (pubIds.getPmid().equals(publicationProvided.getPmid().getContent()) || pubIds.getPmcid().equals(publicationProvided.getPmcid().getContent()) || pubIds.getDoi().equals(publicationProvided.getDoi().getContent())) {
							publicationsProvidedMerged.add(pubIds.toString());
							break;
						}
					}
				}
				if (publicationsProvidedMerge || !resultI.getSuggestions().isEmpty()) {
					if (!publicationsProvidedMerge && !resultI.getSuggestions().get(0).confident()) {
						break;
					}
					for (int j = i + 1; j < results.size(); ++j) {
						if (removeResult.contains(j)) {
							continue;
						}
						Result2 resultJ = results.get(j);
						if (publicationsProvidedMerge || !resultJ.getSuggestions().isEmpty()) {
							if (!publicationsProvidedMerge && !resultJ.getSuggestions().get(0).confident()) {
								break;
							}
							boolean publicationProvidedMerge = false;
							if (publicationsProvidedMerge) {
								PubIds pubIds = resultJ.getPubIds().get(0);
								for (Publication publicationProvided : publicationsProvided) {
									if (pubIds.getPmid().equals(publicationProvided.getPmid().getContent()) || pubIds.getPmcid().equals(publicationProvided.getPmcid().getContent()) || pubIds.getDoi().equals(publicationProvided.getDoi().getContent())) {
										if (!publicationsProvidedMerged.contains(pubIds.toString())) {
											publicationsProvidedMerged.add(pubIds.toString());
											publicationProvidedMerge = true;
										}
										break;
									}
								}
							}
							if (publicationProvidedMerge || resultI.getSuggestions().get(0).getExtracted().equals(resultJ.getSuggestions().get(0).getExtracted())) {
								resultI.addPubIds(resultJ.getPubIds().get(0));

								resultI.addTitle(resultJ.getTitle().get(0));
								resultI.addToolTitleOthers(resultJ.getToolTitleOthers().get(0));
								resultI.addToolTitleExtractedOriginal(resultJ.getToolTitleExtractedOriginal().get(0));
								resultI.addToolTitle(resultJ.getToolTitle().get(0));
								resultI.addToolTitlePruned(resultJ.getToolTitlePruned().get(0));
								resultI.addToolTitleAcronym(resultJ.getToolTitleAcronym().get(0));
								resultI.addAbstractSentences(resultJ.getAbstractSentences().get(0));
								resultI.addOa(resultJ.isOa().get(0));
								resultI.addJournalTitle(resultJ.getJournalTitle().get(0));
								resultI.addPubDate(resultJ.getPubDate().get(0));
								resultI.addPubDateHuman(resultJ.getPubDateHuman().get(0));
								resultI.addCitationsCount(resultJ.getCitationsCount().get(0));
								resultI.addCitationsTimestamp(resultJ.getCitationsTimestamp().get(0));
								resultI.addCitationsTimestampHuman(resultJ.getCitationsTimestampHuman().get(0));

								resultI.addCorrespAuthor(resultJ.getCorrespAuthor().get(0));

								for (Iterator<Suggestion2> iterI = resultI.getSuggestions().iterator(); iterI.hasNext(); ) {
									Suggestion2 suggestionI = iterI.next();
									for (Iterator<Suggestion2> iterJ = resultJ.getSuggestions().iterator(); iterJ.hasNext(); ) {
										Suggestion2 suggestionJ = iterJ.next();
										if (suggestionI.getExtracted().equals(suggestionJ.getExtracted())) {
											if (suggestionI.compareTo(suggestionJ) > 0) {
												suggestionJ.addLinksAbstract(suggestionI.getLinksAbstract());
												suggestionJ.addLinksFulltext(suggestionI.getLinksFulltext());
												iterI.remove();
											} else {
												suggestionI.addLinksAbstract(suggestionJ.getLinksAbstract());
												suggestionI.addLinksFulltext(suggestionJ.getLinksFulltext());
												iterJ.remove();
											}
											break;
										}
									}
								}
								for (Suggestion2 suggestionJ : resultJ.getSuggestions()) {
									resultI.addSuggestion(suggestionJ);
								}
								Collections.sort(resultI.getSuggestions());

								resultI.addLeftoverLinksAbstract(resultJ.getLeftoverLinksAbstract().get(0));
								resultI.addLeftoverLinksFulltext(resultJ.getLeftoverLinksFulltext().get(0));

								removeResult.add(j);
							}
						}
					}
				}
			}
			for (Iterator<Integer> it = removeResult.descendingIterator(); it.hasNext(); ) {
				results.remove(it.next().intValue());
			}
			logger.info(mainMarker, "{}Merged {} pass1 results to {} pass2 results", logPrefix, results1.size(), results.size());

			if (!results.isEmpty()) {
				Result2 result = results.get(0);
				if (!result.getSuggestions().isEmpty()) {
					if (nameProvided != null && !nameProvided.isEmpty()) {
						String nameProvidedProcessed = String.join(" ", preProcessor.process(nameProvided));
						int toFirst = -1;
						for (int i = 0; i < result.getSuggestions().size(); ++i) {
							if (result.getSuggestions().get(i).getProcessed().equals(nameProvidedProcessed)) {
								toFirst = i;
								break;
							}
						}
						if (toFirst > 0) {
							Suggestion2 suggestion = result.getSuggestions().remove(toFirst);
							result.getSuggestions().add(0, suggestion);
						}
					}
					if (webpageUrlsProvided != null && !webpageUrlsProvided.isEmpty()) {
						Suggestion2 suggestion = result.getSuggestions().get(0);
						List<String> webpageUrlsProvidedNotAdded = new ArrayList<>();
						for (String webpageUrlProvided : webpageUrlsProvided) {
							boolean added = false;
							for (String abstractLink : suggestion.getLinksAbstract()) {
								if (Common.trimUrl(abstractLink).equals(Common.trimUrl(webpageUrlProvided))) {
									added = true;
									break;
								}
							}
							if (!added) {
								if (!Common.SCHEMA_START.matcher(webpageUrlProvided).find()) {
									webpageUrlProvided = "http://" + webpageUrlProvided;
								}
								webpageUrlsProvidedNotAdded.add(webpageUrlProvided);
							}
						}
						suggestion.addLinksAbstract(webpageUrlsProvidedNotAdded);
					}
				}
			}

			logger.info(mainMarker, "{}Filling same suggestions field for non-merged results", logPrefix, results1.size(), results.size());
			for (int i = 0; i < results.size() - 1; ++i) {
				Result2 resultI = results.get(i);
				if (!resultI.getSuggestions().isEmpty()) {
					for (int j = i + 1; j < results.size(); ++j) {
						Result2 resultJ = results.get(j);
						if (!resultJ.getSuggestions().isEmpty()) {
							if (resultI.getSuggestions().get(0).getExtracted().equals(resultJ.getSuggestions().get(0).getExtracted())) {
								resultI.addSameSuggestion(resultJ.getPubIds().get(0));
								resultJ.addSameSuggestion(resultI.getPubIds().get(0));
							}
						}
					}
				}
			}

			logger.info(mainMarker, "{}Processing bio.tools names and links", logPrefix);
			List<List<String>> queryNamesExtracted = new ArrayList<>();
			List<String> queryNamesProcessed = new ArrayList<>();
			List<List<String>> queryLinks = new ArrayList<>();
			for (Tool biotool : biotools) {
				List<String> queryNameExtracted = preProcessor.extract(biotool.getName());
				List<String> queryNameProcessed = preProcessor.process(biotool.getName(), queryNameExtracted);
				queryNamesExtracted.add(Arrays.asList(Common.BIOTOOLS_EXTRACTED_VERSION_TRIM.matcher(String.join(" ", queryNameExtracted)).replaceFirst("").split(" ")));
				queryNamesProcessed.add(Common.BIOTOOLS_PROCESSED_VERSION_TRIM.matcher(String.join(" ", queryNameProcessed)).replaceFirst(""));
				List<String> links = new ArrayList<>();
				links.add(biotool.getHomepage());
				if (biotool.getLink() != null) {
					links.addAll(biotool.getLink().stream().map(l -> l.getUrl()).collect(Collectors.toList()));
				}
				if (biotool.getDownload() != null) {
					links.addAll(biotool.getDownload().stream().map(l -> l.getUrl()).collect(Collectors.toList()));
				}
				if (biotool.getDocumentation() != null) {
					links.addAll(biotool.getDocumentation().stream().map(l -> l.getUrl()).collect(Collectors.toList()));
				}
				queryLinks.add(links.stream()
					.map(l -> Common.trimUrl(l.trim()))
					.filter(l -> !l.isEmpty())
					.collect(Collectors.toList()));
			}

			logger.info(mainMarker, "{}Finding existing bio.tools entries", logPrefix);

			int resultIndex = 0;
			long start = System.currentTimeMillis();
			for (Result2 result : results) {
				++resultIndex;
				System.err.print(PubFetcher.progress(resultIndex, results.size(), start) + "  \r");

				List<Boolean> oneMatches = new ArrayList<>();
				List<Boolean> allMatches = new ArrayList<>();
				List<Set<PubIds>> notMatches = new ArrayList<>();
				for (int i = 0; i < biotools.size(); ++i) {
					Tool biotool = biotools.get(i);
					boolean oneMatch = false;
					boolean allMatch = true;
					Set<PubIds> notMatch = null;
					for (PubIds pubIds : result.getPubIds()) {
						boolean match = false;
						if (biotool.getPublication() != null) {
							for (org.edamontology.edammap.core.input.json.Publication publicationIds : biotool.getPublication()) {
								if (!pubIds.getPmid().isEmpty() && publicationIds.getPmid() != null && publicationIds.getPmid().trim().equals(pubIds.getPmid())
										|| !pubIds.getPmcid().isEmpty() && publicationIds.getPmcid() != null && publicationIds.getPmcid().trim().equals(pubIds.getPmcid())
										|| !pubIds.getDoi().isEmpty() && publicationIds.getDoi() != null && PubFetcher.normaliseDoi(publicationIds.getDoi().trim()).equals(pubIds.getDoi())) {
									match = true;
									break;
								}
							}
						}
						if (match) {
							oneMatch = true;
						} else {
							allMatch = false;
							if (notMatch == null) {
								notMatch = new LinkedHashSet<>();
							}
							notMatch.add(pubIds);
						}
					}
					oneMatches.add(oneMatch);
					allMatches.add(allMatch);
					notMatches.add(notMatch);
				}
				for (int i = 0; i < result.getSuggestions().size(); ++i) {
					Suggestion2 suggestion = result.getSuggestions().get(i);
					List<Integer> publicationAndNameExisting = null;
					List<Integer> nameExistingSomePublicationDifferent = null;
					List<Set<PubIds>> nameExistingSomePublicationDifferentPubIds = null;
					List<Integer> somePublicationExistingNameDifferent = null;
					List<Set<PubIds>> somePublicationExistingNameDifferentPubIds = null;
					List<Integer> nameExistingPublicationDifferent = null;
					List<Set<PubIds>> nameExistingPublicationDifferentPubIds = null;
					for (int j = 0; j < biotools.size(); ++j) {
						Tool biotool = biotools.get(j);
						if (suggestion.getExtracted().equals(biotool.getName())) {
							if (allMatches.get(j)) {
								if (publicationAndNameExisting == null) {
									publicationAndNameExisting = new ArrayList<>();
								}
								publicationAndNameExisting.add(j);
							} else if (oneMatches.get(j)) {
								if (nameExistingSomePublicationDifferent == null) {
									nameExistingSomePublicationDifferent = new ArrayList<>();
								}
								nameExistingSomePublicationDifferent.add(j);
								if (nameExistingSomePublicationDifferentPubIds == null) {
									nameExistingSomePublicationDifferentPubIds = new ArrayList<>();
								}
								nameExistingSomePublicationDifferentPubIds.add(notMatches.get(j));
							} else {
								if (nameExistingPublicationDifferent == null) {
									nameExistingPublicationDifferent = new ArrayList<>();
								}
								nameExistingPublicationDifferent.add(j);
								if (nameExistingPublicationDifferentPubIds == null) {
									nameExistingPublicationDifferentPubIds = new ArrayList<>();
								}
								nameExistingPublicationDifferentPubIds.add(notMatches.get(j));
							}
						} else if (oneMatches.get(j)) {
							if (somePublicationExistingNameDifferent == null) {
								somePublicationExistingNameDifferent = new ArrayList<>();
							}
							somePublicationExistingNameDifferent.add(j);
							if (somePublicationExistingNameDifferentPubIds == null) {
								somePublicationExistingNameDifferentPubIds = new ArrayList<>();
							}
							somePublicationExistingNameDifferentPubIds.add(notMatches.get(j));
						}
					}
					suggestion.setPublicationAndNameExisting(publicationAndNameExisting);
					suggestion.setNameExistingSomePublicationDifferent(nameExistingSomePublicationDifferent);
					suggestion.setNameExistingSomePublicationDifferentPubIds(nameExistingSomePublicationDifferentPubIds);
					suggestion.setSomePublicationExistingNameDifferent(somePublicationExistingNameDifferent);
					suggestion.setSomePublicationExistingNameDifferentPubIds(somePublicationExistingNameDifferentPubIds);
					suggestion.setNameExistingPublicationDifferent(nameExistingPublicationDifferent);
					suggestion.setNameExistingPublicationDifferentPubIds(nameExistingPublicationDifferentPubIds);

					if (i == 0) {
						String suggestionProcessed = Common.BIOTOOLS_PROCESSED_VERSION_TRIM.matcher(result.getSuggestions().get(i).getProcessed()).replaceFirst("");
						String suggestionProcessedCompare = NOT_ALPHANUM.matcher(suggestionProcessed).replaceAll("");
						if (!suggestionProcessed.isEmpty()) {
							for (int j = 0; j < queryNamesProcessed.size(); ++j) {
								String biotoolsIdCompare = NOT_ALPHANUM.matcher(Common.BIOTOOLS_PROCESSED_VERSION_TRIM.matcher(biotools.get(j).getBiotoolsID().toLowerCase(Locale.ROOT)).replaceFirst("")).replaceAll("");
								if (suggestionProcessed.equals(queryNamesProcessed.get(j)) || suggestionProcessedCompare.equals(biotoolsIdCompare)) {
									if ((publicationAndNameExisting == null || !publicationAndNameExisting.contains(j))
											&& (nameExistingSomePublicationDifferent == null || !nameExistingSomePublicationDifferent.contains(j))
											&& (somePublicationExistingNameDifferent == null || !somePublicationExistingNameDifferent.contains(j))
											&& (nameExistingPublicationDifferent == null || !nameExistingPublicationDifferent.contains(j))) {
										result.addNameMatch(j);
									}
								}
							}
						}
						LinkedHashMap<Integer, List<String>> linkMatchMap = new LinkedHashMap<>();
						for (String suggestionLink : result.getSuggestions().get(i).getLinksAbstract()) {
							linksMatch(linkMatchMap, suggestionLink, suggestion, queryLinks);
						}
						for (String suggestionLink : result.getSuggestions().get(i).getLinksFulltext()) {
							linksMatch(linkMatchMap, suggestionLink, suggestion, queryLinks);
						}
						for (Map.Entry<Integer, List<String>> linkMatchEntry : linkMatchMap.entrySet()) {
							result.addLinkMatch(linkMatchEntry.getKey(), linkMatchEntry.getValue());
						}
						String suggestionExtracted = Common.BIOTOOLS_EXTRACTED_VERSION_TRIM.matcher(result.getSuggestions().get(i).getExtracted()).replaceFirst("");
						if (!suggestionExtracted.isEmpty()) {
							for (String suggestionExtractedWord : suggestionExtracted.split(" ")) {
								List<Integer> nameWordMatchPart = new ArrayList<>();
								for (int j = 0; j < queryNamesExtracted.size(); ++j) {
									if (queryNamesExtracted.get(j).contains(suggestionExtractedWord)) {
										if ((publicationAndNameExisting == null || !publicationAndNameExisting.contains(j))
												&& (nameExistingSomePublicationDifferent == null || !nameExistingSomePublicationDifferent.contains(j))
												&& (somePublicationExistingNameDifferent == null || !somePublicationExistingNameDifferent.contains(j))
												&& (nameExistingPublicationDifferent == null || !nameExistingPublicationDifferent.contains(j))
												&& !result.getNameMatch().contains(j) && !result.getLinkMatch().contains(j) && !result.getNameWordMatch().contains(j)) {
											nameWordMatchPart.add(j);
										}
									}
								}
								if (nameWordMatchPart.size() >= 1 && nameWordMatchPart.size() <= NAME_WORD_MATCH_LIMIT) {
									for (Integer j : nameWordMatchPart) {
										result.addNameWordMatch(j);
									}
								}
							}
						}
					}
				}
			}

			logger.info(mainMarker, "{}Dividing links", logPrefix);
			for (Result2 result : results) {
				String name = (!result.getSuggestions().isEmpty() ? result.getSuggestions().get(0).getExtracted() : "");
				for (Suggestion2 suggestion : result.getSuggestions()) {
					List<BiotoolsLink<LinkType>> linkLinksAbstract = new ArrayList<>();
					List<BiotoolsLink<DownloadType>> downloadLinksAbstract = new ArrayList<>();
					List<BiotoolsLink<DocumentationType>> documentationLinksAbstract = new ArrayList<>();
					Common.makeBiotoolsLinks(suggestion.getLinksAbstract(), linkLinksAbstract, downloadLinksAbstract, documentationLinksAbstract);
					removeBroken(linkLinksAbstract, suggestion.getBrokenLinks(), db, false, name);
					removeBroken(downloadLinksAbstract, suggestion.getBrokenLinks(), db, false, name);
					removeBroken(documentationLinksAbstract, suggestion.getBrokenLinks(), db, true, name);
					String homepage = chooseHomepage(suggestion.getLinksAbstract(), linkLinksAbstract, documentationLinksAbstract, db);
					List<BiotoolsLink<LinkType>> linkLinksFulltext = new ArrayList<>();
					List<BiotoolsLink<DownloadType>> downloadLinksFulltext = new ArrayList<>();
					List<BiotoolsLink<DocumentationType>> documentationLinksFulltext = new ArrayList<>();
					Common.makeBiotoolsLinks(suggestion.getLinksFulltext(), linkLinksFulltext, downloadLinksFulltext, documentationLinksFulltext);
					removeBroken(linkLinksFulltext, suggestion.getBrokenLinks(), db, false, name);
					removeBroken(downloadLinksFulltext, suggestion.getBrokenLinks(), db, false, name);
					removeBroken(documentationLinksFulltext, suggestion.getBrokenLinks(), db, true, name);
					if (homepage == null) {
						homepage = chooseHomepage(suggestion.getLinksFulltext(), linkLinksFulltext, documentationLinksFulltext, db);
					}
					if (homepage == null) {
						for (String link : suggestion.getLinksAbstract()) {
							link = Common.prependHttp(link);
							if (!Common.DOWNLOAD_EXT.matcher(link).find() && Common.BIOTOOLS_SCHEMA_URLFTP_PATTERN.matcher(link).matches()) {
								homepage = link;
								suggestion.setHomepageBroken(true);
								break;
							}
						}
					}
					if (homepage == null) {
						for (String link : suggestion.getLinksFulltext()) {
							link = Common.prependHttp(link);
							if (!Common.DOWNLOAD_EXT.matcher(link).find() && Common.BIOTOOLS_SCHEMA_URLFTP_PATTERN.matcher(link).matches()) {
								homepage = link;
								suggestion.setHomepageBroken(true);
								break;
							}
						}
					}
					if (homepage != null) {
						suggestion.setHomepage(homepage);
					} else {
						for (PubIds pubIds : result.getPubIds()) {
							homepage = PubFetcher.getPmidLink(pubIds.getPmid());
							if (homepage == null) homepage = PubFetcher.getPmcidLink(pubIds.getPmcid());
							if (homepage == null) homepage = PubFetcher.getDoiLink(pubIds.getDoi());
							if (homepage != null) {
								suggestion.setHomepage(homepage);
								break;
							}
						}
						if (homepage == null) {
							suggestion.setHomepage("https://bio.tools");
						}
						suggestion.setHomepageMissing(true);
					}
					suggestion.addLinkLinks(linkLinksAbstract);
					suggestion.addLinkLinks(linkLinksFulltext);
					suggestion.addDownloadLinks(downloadLinksAbstract);
					suggestion.addDownloadLinks(downloadLinksFulltext);
					suggestion.addDocumentationLinks(documentationLinksAbstract);
					suggestion.addDocumentationLinks(documentationLinksFulltext);
					suggestion.removeHomepageFromLinks();
				}
			}

			List<Diff> diffs = new ArrayList<>();
			List<Tool> tools = new ArrayList<>();

			logger.info(mainMarker, "{}Writing {} pass2 results to {}", logPrefix, results.size(), resultsPath.toString());
			resultIndex = 0;
			start = System.currentTimeMillis();
			for (Result2 result : results) {
				++resultIndex;
				System.err.print(PubFetcher.progress(resultIndex, results.size(), start) + "  \r");
				writeResult(result, db, resultsWriter, biotools, licenses, languages, languageKeywords, scrape, preProcessor, diffs, tools, notAbstract, notTitle, includeAll);
			}

			logger.info(mainMarker, "{}Writing {} bio.tools diffs to {}", logPrefix, diffs.size(), diffPath.toString());
			for (Diff diff : diffs) {
				if (!diff.include()) {
					continue;
				}
				Tool biotool = biotools.get(diff.getExisting());
				writeField(diffWriter, biotool.getBiotoolsID());
				writeField(diffWriter, String.valueOf(diff.getScoreScore2()));
				String publicationBiotools = null;
				if (biotool.getPublication() != null && (!diff.getModifyPublications().isEmpty() || diff.getAddPublications() != null && !diff.getAddPublications().isEmpty() || diff.getModifyName() != null && !diff.getModifyName().isEmpty())) {
					publicationBiotools = biotool.getPublication().stream().map(pubIds -> "[" + PublicationIds.toString(pubIds.getPmid(), pubIds.getPmcid(), pubIds.getDoi(), false) + "]").collect(Collectors.joining(" | "));
				}
				writeField(diffWriter, publicationBiotools);
				writeField(diffWriter, diff.getModifyPublications().stream().map(pubIds -> pubIds.toString()).collect(Collectors.joining(" | ")));
				writeField(diffWriter, diff.getAddPublications() != null ? diff.getAddPublications().stream().map(pubIds -> pubIds.toString()).collect(Collectors.joining(" | ")) : null);
				writeField(diffWriter, diff.getModifyName() != null && !diff.getModifyName().isEmpty() ? biotool.getName() : null);
				writeField(diffWriter, diff.getModifyName());
				writeField(diffWriter, diff.getPossiblyRelated() != null ? diff.getPossiblyRelated().stream().map(e -> biotools.get(e)).map(q -> q.getBiotoolsID() + " (" + q.getName() + ")").collect(Collectors.joining(" | ")) : null);
				writeField(diffWriter, diff.getModifyHomepage() != null && !diff.getModifyHomepage().isEmpty() ? currentHomepage(biotool, db) : null);
				writeField(diffWriter, diff.getModifyHomepage());
				String linkBiotools = null;
				if (biotool.getLink() != null && !diff.getAddLinks().isEmpty()) {
					linkBiotools = biotool.getLink().stream().map(l -> l.getUrl() + " (" + l.toStringType() + ")").collect(Collectors.joining(" | "));
				}
				writeField(diffWriter, linkBiotools);
				writeField(diffWriter, diff.getAddLinks().stream().map(l -> l.getUrl() + " (" + l.getType() + ")").collect(Collectors.joining(" | ")));
				String downloadBiotools = null;
				if (biotool.getDownload() != null && !diff.getAddDownloads().isEmpty()) {
					downloadBiotools = biotool.getDownload().stream().map(l -> l.getUrl() + " (" + l.getType() + ")").collect(Collectors.joining(" | "));
				}
				writeField(diffWriter, downloadBiotools);
				writeField(diffWriter, diff.getAddDownloads().stream().map(l -> l.getUrl() + " (" + l.getType() + ")").collect(Collectors.joining(" | ")));
				String documentationBiotools = null;
				if (biotool.getDocumentation() != null && !diff.getAddDocumentations().isEmpty()) {
					documentationBiotools = biotool.getDocumentation().stream().map(l -> l.getUrl() + " (" + l.toStringType() + ")").collect(Collectors.joining(" | "));
				}
				writeField(diffWriter, documentationBiotools);
				writeField(diffWriter, diff.getAddDocumentations().stream().map(l -> l.getUrl() + " (" + l.getType() + ")").collect(Collectors.joining(" | ")));
				writeField(diffWriter, diff.getModifyLicense() != null && !diff.getModifyLicense().isEmpty() ? biotool.getLicense() : null);
				writeField(diffWriter, diff.getModifyLicense() != null ? diff.getModifyLicense().toString() : null);
				String languageBiotools = null;
				if (biotool.getLanguage() != null && !diff.getAddLanguages().isEmpty()) {
					languageBiotools = String.join(" | ", biotool.getLanguage());
				}
				writeField(diffWriter, languageBiotools);
				writeField(diffWriter, diff.getAddLanguages().stream().map(l -> l.toString()).collect(Collectors.joining(" | ")));
				String creditBiotools = null;
				if (biotool.getCredit() != null && (!diff.getModifyCredits().isEmpty() || !diff.getAddCredits().isEmpty())) {
					creditBiotools = biotool.getCredit().stream().map(c -> Arrays.asList(c.getName(), c.getOrcidid(), c.getEmail(), c.getUrl()).stream().filter(e -> e != null && !e.isEmpty()).collect(Collectors.joining(", "))).collect(Collectors.joining(" | "));
				}
				writeField(diffWriter, creditBiotools);
				writeField(diffWriter, diff.getModifyCredits().stream().map(c -> c.toString()).collect(Collectors.joining(" | ")));
				writeField(diffWriter, diff.getAddCredits().stream().map(c -> c.toString()).collect(Collectors.joining(" | ")), true);
			}

			List<Tool> toolsUniq = new ArrayList<>();
			for (Tool tool : tools) {
				boolean existing = false;
				for (Tool toolUniq : toolsUniq) {
					if (tool.getName().equals(toolUniq.getName())) {
						for (org.edamontology.edammap.core.input.json.Publication publicationTool : tool.getPublication()) {
							PubIds pubIdsTool = new PubIds(publicationTool.getPmid(), publicationTool.getPmcid(), publicationTool.getDoi());
							for (org.edamontology.edammap.core.input.json.Publication publicationToolUniq : toolUniq.getPublication()) {
								PubIds pubIdsToolUniq = new PubIds(publicationToolUniq.getPmid(), publicationToolUniq.getPmcid(), publicationToolUniq.getDoi());
								if (pubIdsTool.equals(pubIdsToolUniq)) {
									existing = true;
								}
							}
						}
						if (existing) {
							logger.warn("New tool {} ({}) already proposed, omitting", tool.getName(), tool.getPublication().stream().map(p -> "[" + PublicationIds.toString(p.getPmid(), p.getPmcid(), p.getDoi(), false) + "]").collect(Collectors.joining(", ")));
							break;
						}
					}
				}
				if (!existing) {
					toolsUniq.add(tool);
				}
			}

			logger.info(mainMarker, "{}Writing {} new bio.tools entries to {}", logPrefix, toolsUniq.size(), newPath.toString());
			org.edamontology.edammap.core.output.Json.outputBiotools(newWriter, toolsUniq);

			return toolsUniq;
		} finally {
			if (db != null && dbProvided == null) {
				db.close();
			}
		}
	}
}
