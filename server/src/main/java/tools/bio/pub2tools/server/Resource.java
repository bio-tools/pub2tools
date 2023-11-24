/*
 * Copyright © 2023 Erik Jaaniso
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

package tools.bio.pub2tools.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.util.Header;

import org.edamontology.pubfetcher.core.common.FetcherPrivateArgs;
import org.edamontology.pubfetcher.core.common.IllegalRequestException;
import org.edamontology.pubfetcher.core.common.PubFetcher;
import org.edamontology.pubfetcher.core.db.DatabaseEntryType;
import org.edamontology.pubfetcher.core.db.publication.Publication;

import org.edamontology.edammap.core.args.CoreArgs;
import org.edamontology.edammap.core.benchmarking.Results;
import org.edamontology.edammap.core.edam.Concept;
import org.edamontology.edammap.core.edam.EdamUri;
import org.edamontology.edammap.core.input.DatabaseEntryId;
import org.edamontology.edammap.core.input.ServerInput;
import org.edamontology.edammap.core.input.json.Tool;
import org.edamontology.edammap.core.output.DatabaseEntryEntry;
import org.edamontology.edammap.core.output.Json;
import org.edamontology.edammap.core.output.JsonType;
import org.edamontology.edammap.core.output.Output;
import org.edamontology.edammap.core.preprocessing.PreProcessor;
import org.edamontology.edammap.core.processing.ConceptProcessed;
import org.edamontology.edammap.core.processing.Processor;
import org.edamontology.edammap.core.processing.QueryProcessed;
import org.edamontology.edammap.core.query.Query;
import org.edamontology.edammap.core.query.QueryType;
import org.edamontology.edammap.server.ParamParse;
import org.edamontology.edammap.server.ServerPrivateArgsBase;

import tools.bio.pub2tools.core.Common;
import tools.bio.pub2tools.core.Pass1;
import tools.bio.pub2tools.core.Pass2;

@Path("/")
public class Resource extends org.edamontology.edammap.server.ResourceBase {

	private static final Logger logger = LogManager.getLogger();

	static String runGet(MultivaluedMap<String, String> params, Request request) {
		try {
			logger.info("GET {} from {}", params, request.getRemoteAddr());
			CoreArgs args = newCoreArgs(params, false, Server.args.getProcessorArgs(), Server.args.getFetcherPrivateArgs());
			return Page.get(args);
		} catch (Throwable e) {
			logger.error("Exception!", e);
			throw e;
		}
	}

	@GET
	@Produces(MediaType.TEXT_HTML + ";charset=utf-8")
	public Response get(@Context UriInfo ui, @Context Request request) {
		String responseText = runGet(ui.getQueryParameters(), request);
		return Response.ok(responseText).header(Header.ContentLength.toString(), responseText.getBytes().length).build();
	}

	@SuppressWarnings({ "deprecation", "unchecked" })
	@Override
	protected PostResult runPost(MultivaluedMap<String, String> params, Tool tool, Request request, boolean isJson) throws IOException, URISyntaxException, ParseException {
		isJson = true;

		logger.info("POST {} from {}", params, request.getRemoteAddr());

		long start = System.currentTimeMillis();
		Instant startInstant = Instant.ofEpochMilli(start);
		logger.info("Start: {}", startInstant);

		String jsonVersion = jsonVersion(params, isJson);

		CoreArgs coreArgs = newCoreArgs(params, isJson, Server.args.getProcessorArgs(), Server.args.getFetcherPrivateArgs());

		ServerInput serverInput = getServerInput(params, isJson, tool, false, true);

		checkInput(serverInput, tool);

		Step step = Step.all;
		if (isJson) {
			Enum<?> valueEnum;
			if ((valueEnum = ParamParse.getParamEnum(params, JsonOutput.STEP_ID, Step.class, isJson)) != null) {
				if ((Step) valueEnum == Step.withoutmap) {
					step = Step.withoutmap;
				} else if ((Step) valueEnum == Step.map) {
					step = Step.map;
				}
			}
		}

		if (step == Step.map && tool == null) {
			throw new IllegalRequestException("Input must be specified in \"tool\" for step \"map\"");
		}

		String uuidDirPrefix = Server.args.getServerPrivateArgs().getFiles() + "/";
		String uuid = getUuid("-" + step.name(), uuidDirPrefix, Server.version, startInstant);
		boolean toolMissingId = false;
		if (serverInput != null) {
			serverInput.setId(uuid);
		} else if (tool.getBiotoolsID() == null || tool.getBiotoolsID().isEmpty()) {
			toolMissingId = true;
			tool.setBiotoolsID(uuid);
		}
		String jsonOutput = null;
		if (isJson) {
			jsonOutput = uuid + "/results.json";
		}
		java.nio.file.Path outputPath = Paths.get(uuidDirPrefix + uuid);

		Query query = null;
		Tool toolOut = null;
		if (step == Step.withoutmap || step == Step.all) {
			boolean stemming = coreArgs.getPreProcessorArgs().isStemming();
			coreArgs.getPreProcessorArgs().setStemming(false);
			PreProcessor preProcessorPass = new PreProcessor(coreArgs.getPreProcessorArgs(), Server.stopwordsAll.get(coreArgs.getPreProcessorArgs().getStopwords()));
			coreArgs.getPreProcessorArgs().setStemming(stemming);

			query = getQuery(serverInput, tool, toolMissingId, false, false);

			if (query.getPublicationIds() == null) {
				throw new IllegalRequestException("At least one publication must be specified");
			}
			List<String> webpageUrls = null;
			if (query.getWebpageUrls() != null) {
				webpageUrls = query.getWebpageUrls().stream().map(l -> l.getUrl()).collect(Collectors.toList());
			}

			List<DatabaseEntryId> ids = new ArrayList<>();
			for (Object id : query.getPublicationIds()) {
				ids.add(new DatabaseEntryId(id, DatabaseEntryType.publication));
			}
			logger.info("Fetching {} publications using PubFetcher", ids.size());
			long startPublications = System.currentTimeMillis();
			List<DatabaseEntryEntry> databaseEntries = Server.processor.getDatabaseEntries(ids, coreArgs.getFetcherArgs(), Server.args.getServerPrivateArgs().getFetchingThreads());
			List<Publication> publications = databaseEntries.stream().map(e -> (Publication) e.getEntry()).collect(Collectors.toList());
			logger.info("Fetching {} publications took {}s", ids.size(), (System.currentTimeMillis() - startPublications) / 1000.0);

			logger.info("Running pass1");
			long startPass1 = System.currentTimeMillis();
			Pass1.run(outputPath, preProcessorPass, "", Server.idf, publications, query.getName(), webpageUrls);
			logger.info("Running pass1 took {}s", (System.currentTimeMillis() - startPass1) / 1000.0);

			String webFile = outputPath.resolve(Common.WEB_FILE).toString();
			String docFile = outputPath.resolve(Common.DOC_FILE).toString();

			List<String> webUrls = PubFetcher.webFile(Collections.singletonList(webFile));
			logger.info("Loaded {} webpage URLs from {}", webUrls.size(), webFile);
			List<DatabaseEntryId> idsWeb = new ArrayList<>();
			for (Object id : new LinkedHashSet<>(webUrls)) {
				idsWeb.add(new DatabaseEntryId(id, DatabaseEntryType.webpage));
			}
			logger.info("Fetching {} webpages using PubFetcher", idsWeb.size());
			long startWebpages = System.currentTimeMillis();
			Server.processor.getDatabaseEntries(idsWeb, coreArgs.getFetcherArgs(), Server.args.getServerPrivateArgs().getFetchingThreads());
			logger.info("Fetching {} webpages took {}s", idsWeb.size(), (System.currentTimeMillis() - startWebpages) / 1000.0);

			List<String> docUrls = PubFetcher.webFile(Collections.singletonList(docFile));
			logger.info("Loaded {} doc URLs from {}", docUrls.size(), docFile);
			List<DatabaseEntryId> idsDoc = new ArrayList<>();
			for (Object id : new LinkedHashSet<>(docUrls)) {
				idsDoc.add(new DatabaseEntryId(id, DatabaseEntryType.doc));
			}
			logger.info("Fetching {} docs using PubFetcher", idsDoc.size());
			long startDocs = System.currentTimeMillis();
			Server.processor.getDatabaseEntries(idsDoc, coreArgs.getFetcherArgs(), Server.args.getServerPrivateArgs().getFetchingThreads());
			logger.info("Fetching {} docs took {}s", idsDoc.size(), (System.currentTimeMillis() - startDocs) / 1000.0);

			logger.info("Running pass2");
			long startPass2 = System.currentTimeMillis();
			List<Tool> tools = Pass2.run(outputPath, preProcessorPass, coreArgs.getFetcherArgs(), "", Server.idf, Server.biotools, Server.processor.getDatabase(), true, publications, query.getName(), webpageUrls);
			logger.info("Running pass2 took {}s", (System.currentTimeMillis() - startPass2) / 1000.0);

			if (tools.isEmpty()) {
				toolOut = new Tool();
			} else {
				toolOut = tools.get(0);
				((Map<String, Object>) toolOut.getOthers().get(Pass2.TOOL_STATUS)).put("toolsExtra", null);
			}
			if (tools.size() > 1) {
				List<String> toolsExtra = new ArrayList<>();
				for (int i = 1; i < tools.size(); ++i) {
					String extra = tools.get(i).getName();
					if (!(Boolean) ((Map<String, Object>) tools.get(i).getOthers().get(Pass2.TOOL_STATUS)).get("homepageMissing")) {
						extra += " (" + tools.get(i).getHomepage() + ")";
					}
					toolsExtra.add(extra);
				}
				((Map<String, Object>) toolOut.getOthers().get(Pass2.TOOL_STATUS)).put("toolsExtra", toolsExtra);
			}
			if (toolOut.getDescription() != null) {
				toolOut.setDescription(toolOut.getDescription().split("\n\n\\|\\|\\| ")[0]);
			}
			if (step == Step.all) {
				if (toolOut.getBiotoolsID() == null || toolOut.getBiotoolsID().isEmpty()) {
					toolMissingId = true;
					toolOut.setBiotoolsID(uuid + "-map");
				}
			}
		} else {
			toolOut = tool;
		}

		Query queryMap = null;
		QueryProcessed processedQuery = null;
		Results results = null;
		if (step == Step.map || step == Step.all) {
			boolean homepageMissing = (toolOut.getOthers().containsKey(Pass2.TOOL_STATUS) ? (Boolean) ((Map<String, Object>) toolOut.getOthers().get(Pass2.TOOL_STATUS)).get("homepageMissing") : false);
			queryMap = getQuery(null, toolOut, toolMissingId, Common.isHomepageDoc(toolOut.getHomepage()), homepageMissing);
			PreProcessor preProcessorMap = new PreProcessor(coreArgs.getPreProcessorArgs(), Server.stopwordsAll.get(coreArgs.getPreProcessorArgs().getStopwords()));
			Map<EdamUri, ConceptProcessed> processedConcepts = getProcessedConcepts(coreArgs, preProcessorMap);
			if (step == Step.all) {
				Integer retryLimit = coreArgs.getFetcherArgs().getRetryLimit();
				coreArgs.getFetcherArgs().setRetryLimit(0);
				processedQuery = getProcessedQuery(coreArgs, Server.idf, Server.idfStemmed, queryMap, preProcessorMap);
				coreArgs.getFetcherArgs().setRetryLimit(retryLimit);
			} else {
				processedQuery = getProcessedQuery(coreArgs, Server.idf, Server.idfStemmed, queryMap, preProcessorMap);
			}
			results = getResults(processedConcepts, queryMap, Collections.singletonList(queryMap), processedQuery, coreArgs, Server.edamBlacklist);
		}

		URI baseLocation = new URI(Server.args.getServerPrivateArgs().isHttpsProxy() ? "https" : request.getScheme(), null, request.getServerName(), Server.args.getServerPrivateArgs().isHttpsProxy() ? 443 : request.getServerPort(), null, null, null);
		URI apiLocation = new URI(baseLocation.getScheme(), null, baseLocation.getHost(), baseLocation.getPort(), "/" + Server.args.getServerPrivateArgs().getPath() + "/api", null, null);
		URI jsonLocation = null;
		if (jsonOutput != null) {
			jsonLocation = new URI(baseLocation.getScheme(), null, baseLocation.getHost(), baseLocation.getPort(), "/" + Server.args.getServerPrivateArgs().getPath() + "/" + jsonOutput, null, null);
		}

		Map<String, String> jsonFields = new LinkedHashMap<>();
		jsonFields.put("api", apiLocation.toString());
		jsonFields.put("json", jsonLocation != null ? jsonLocation.toString() : null);
		jsonFields.put("step", step.name());

		long stop = System.currentTimeMillis();
		logger.info("Stop: {}", Instant.ofEpochMilli(stop));
		logger.info("Total time is {}s", (stop - start) / 1000.0);

		logger.info("Outputting results");

		if (step == Step.map || step == Step.all) {
			Output output = new Output(null, null, outputPath.resolve(Common.MAP_JSON_FILE).toString(), null, QueryType.biotools, true);
			output.output(coreArgs, Server.getArgsMain(false), null, null, 1, 1, Server.concepts,
					Collections.singletonList(queryMap), Collections.singletonList(processedQuery.getWebpages()), Collections.singletonList(processedQuery.getDocs()), Collections.singletonList(processedQuery.getPublications()),
					results, null, start, stop, Server.version, jsonVersion, false);
		}

		JsonType jsonType = JsonType.core;
		Enum<?> valueEnum;
		if ((valueEnum = ParamParse.getParamEnum(params, Json.TYPE_ID, JsonType.class, isJson)) != null) {
			if ((JsonType) valueEnum == JsonType.full) {
				jsonType = JsonType.full;
			}
		}
		String jsonString = JsonOutput.output(coreArgs, Server.getArgsMain(false), jsonFields, jsonType, Server.concepts, query, queryMap,
				processedQuery != null ? processedQuery.getPublications() : null, processedQuery != null ? processedQuery.getWebpages() : null, processedQuery != null ? processedQuery.getDocs() : null,
				results, toolOut, start, stop, Server.version, jsonVersion);
		try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(uuidDirPrefix + jsonOutput), StandardCharsets.UTF_8)) {
			bw.write(jsonString);
		}

		if (isJson) {
			logger.info("POSTED JSON {}", jsonLocation);
		}

		return new PostResult(jsonString, null);
	}

	@Override
	protected FetcherPrivateArgs getFetcherPrivateArgs() {
		return Server.args.getFetcherPrivateArgs();
	}

	@Override
	protected ServerPrivateArgsBase getServerPrivateArgs() {
		return Server.args.getServerPrivateArgs();
	}

	@Override
	protected Map<EdamUri, Concept> getConcepts() {
		return Server.concepts;
	}

	@Override
	protected Processor getProcessor() {
		return Server.processor;
	}
}
