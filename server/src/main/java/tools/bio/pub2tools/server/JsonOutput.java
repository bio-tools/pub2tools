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

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.edamontology.pubfetcher.core.common.Version;
import org.edamontology.pubfetcher.core.db.publication.Publication;
import org.edamontology.pubfetcher.core.db.webpage.Webpage;

import org.edamontology.edammap.core.args.ArgMain;
import org.edamontology.edammap.core.args.CoreArgs;
import org.edamontology.edammap.core.benchmarking.Results;
import org.edamontology.edammap.core.edam.Concept;
import org.edamontology.edammap.core.edam.EdamUri;
import org.edamontology.edammap.core.input.json.Tool;
import org.edamontology.edammap.core.output.Json;
import org.edamontology.edammap.core.output.JsonType;
import org.edamontology.edammap.core.output.Params;
import org.edamontology.edammap.core.query.Link;
import org.edamontology.edammap.core.query.PublicationIdsQuery;
import org.edamontology.edammap.core.query.Query;

import tools.bio.pub2tools.core.Pass2;

public class JsonOutput {

	public static final String STEP_ID = "step";

	public static String output(CoreArgs args, List<ArgMain> argsMain, Map<String, String> jsonFields, JsonType jsonType, Map<EdamUri, Concept> concepts, Query query, Query queryMap, List<Publication> publications, List<Webpage> webpages, List<Webpage> docs, Results results, Tool tool, long start, long stop, Version version, String jsonVersion) throws IOException {
		StringWriter writer = new StringWriter();
		ObjectMapper mapper = new ObjectMapper();
		JsonGenerator generator = Json.createGenerator(writer, null, mapper);
		generator.writeStartObject();

		generator.writeBooleanField("success", true);

		if (jsonVersion != null) {
			generator.writeStringField(Json.VERSION_ID, jsonVersion);
		}

		if (jsonType != null) {
			generator.writeStringField(Json.TYPE_ID, jsonType.name());
		}

		boolean map = (queryMap != null);
		boolean full = (jsonType == JsonType.full || jsonType == JsonType.cli);

		if (jsonFields != null) {
			for (Map.Entry<String, String> jsonField : jsonFields.entrySet()) {
				generator.writeStringField(jsonField.getKey(), jsonField.getValue());
			}
		}

		generator.writeFieldName("generator");
		generator.writeObject(version);

		Json.writeTime(generator, start, stop);

		if (query != null) {
			generator.writeFieldName("query");
			generator.writeStartObject();

			generator.writeStringField(Query.ID, query.getId());
			generator.writeStringField(Query.NAME, query.getName());

			generator.writeFieldName(Query.WEBPAGE_URLS);
			if (query.getWebpageUrls() != null) {
				generator.writeStartArray();
				for (Link webpageUrl : query.getWebpageUrls()) {
					generator.writeString(webpageUrl.getUrl());
				}
				generator.writeEndArray();
			} else {
				generator.writeObject(null);
			}

			generator.writeFieldName(Query.PUBLICATION_IDS);
			if (query.getPublicationIds() != null) {
				generator.writeStartArray();
				for (PublicationIdsQuery publicationIds : query.getPublicationIds()) {
					generator.writeStartObject();
					generator.writeStringField("pmid", publicationIds.getPmid());
					generator.writeStringField("pmcid", publicationIds.getPmcid());
					generator.writeStringField("doi", publicationIds.getDoi());
					generator.writeEndObject();
				}
				generator.writeEndArray();
			} else {
				generator.writeObject(null);
			}

			generator.writeEndObject();
		}

		if (map) {
			generator.writeFieldName("mapping");
			Json.writeMapping(generator, concepts, queryMap, publications, webpages, docs, results.getMappings().get(0), args, true, full);
		}

		generator.writeFieldName("args");
		generator.writeStartObject();
		Params.writeMain(argsMain, generator);
		Params.writeProcessing(args.getProcessorArgs(), generator);
		Params.writePreProcessing(args.getPreProcessorArgs(), generator);
		Params.writeFetching(args.getFetcherArgs(), false, generator);
		if (map) {
			Params.writeMapping(args.getMapperArgs(), generator);
		}
		generator.writeEndObject();

		if (map && full) {
			Params.writeBenchmarking(concepts, Collections.singletonList(queryMap), results, generator);
		}

		Object status = null;
		if (tool.getOthers().containsKey(Pass2.TOOL_STATUS)) {
			status = tool.getOthers().remove(Pass2.TOOL_STATUS);
		}
		mapper.setSerializationInclusion(Include.NON_EMPTY);
		generator.writeFieldName("tool");
		if (map) {
			Json.addAnnotations(args, tool, results.getMappings().get(0), concepts);
		}
		generator.writeObject(tool);
		mapper.setSerializationInclusion(Include.USE_DEFAULTS);
		if (status != null) {
			generator.writeFieldName(Pass2.TOOL_STATUS);
			generator.writeObject(status);
		}

		generator.writeEndObject();
		generator.close();
		return writer.toString();
	}
}
