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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.edamontology.pubfetcher.core.common.Arg;
import org.edamontology.pubfetcher.core.common.BasicArgs;
import org.edamontology.pubfetcher.core.common.Version;
import org.edamontology.pubfetcher.core.db.Database;

import org.edamontology.edammap.core.args.ArgMain;
import org.edamontology.edammap.core.edam.Concept;
import org.edamontology.edammap.core.edam.Edam;
import org.edamontology.edammap.core.edam.EdamUri;
import org.edamontology.edammap.core.idf.Idf;
import org.edamontology.edammap.core.input.BiotoolsFull;
import org.edamontology.edammap.core.input.Json;
import org.edamontology.edammap.core.input.json.Tool;
import org.edamontology.edammap.core.preprocessing.PreProcessor;
import org.edamontology.edammap.core.preprocessing.Stopwords;
import org.edamontology.edammap.core.processing.Processor;
import org.edamontology.edammap.core.query.QueryType;

public final class Server {

	private static final int BIOTOOLS_FILE_TIMEOUT = 30000; // ms
	private static final int BIOTOOLS_FILE_INITIAL_DELAY = 1; // h
	private static final int BIOTOOLS_FILE_DELAY = 23; // h

	private static Logger logger;

	static Version version;

	static ServerArgs args;

	static EnumMap<Stopwords, List<String>> stopwordsAll = new EnumMap<>(Stopwords.class);

	static Set<EdamUri> edamBlacklist;

	static Processor processor = null;

	static Idf idf = null;
	static Idf idfStemmed = null;

	static Map<EdamUri, Concept> concepts;

	static List<Tool> biotools;

	static List<ArgMain> getArgsMain(boolean input) {
		List<ArgMain> argsMain = new ArrayList<>();
		for (Arg<?, ?> arg : args.getArgs()) {
			switch (arg.getId()) {
				default: argsMain.add(new ArgMain(arg.getValue(), arg, false)); break;
			}
		}
		return argsMain;
	}

	private static void getBiotools(String path) throws IOException {
		logger.info("Get all bio.tools content to {}", path);
		int count = BiotoolsFull.get(path, BIOTOOLS_FILE_TIMEOUT, args.getFetcherPrivateArgs().getUserAgent(), false, false);
		logger.info("Got {} bio.tools entries to {}", count, path);
	}

	@SuppressWarnings("unchecked")
	private static void run() throws IOException, ParseException {
		org.edamontology.edammap.server.Server.makeFiles(Server.class, version, args.getServerPrivateArgs(), false, logger);

		for (Stopwords stopwords : Stopwords.values()) {
			stopwordsAll.put(stopwords, PreProcessor.getStopwords(stopwords));
		}

		edamBlacklist = Edam.getBlacklist();

		if (!Files.isReadable(Paths.get(args.getProcessorArgs().getDb()))) {
			logger.info("Init database: {}", args.getProcessorArgs().getDb());
			Database.init(args.getProcessorArgs().getDb());
			logger.info("Init: success");
		}
		processor = new Processor(args.getProcessorArgs(), args.getFetcherPrivateArgs());

		if (args.getProcessorArgs().getIdf() != null && !args.getProcessorArgs().getIdf().isEmpty()) {
			logger.info("Loading IDF from {}", args.getProcessorArgs().getIdf());
			idf = new Idf(args.getProcessorArgs().getIdf());
		}
		if (args.getProcessorArgs().getIdfStemmed() != null && !args.getProcessorArgs().getIdfStemmed().isEmpty()) {
			logger.info("Loading IDF from {}", args.getProcessorArgs().getIdfStemmed());
			idfStemmed = new Idf(args.getProcessorArgs().getIdfStemmed());
		}

		logger.info("Loading concepts from {}", args.getEdam());
		concepts = Edam.load(args.getEdam());

		if (!Files.isReadable(Paths.get(args.getBiotools()))) {
			getBiotools(args.getBiotools());
		}
		biotools = (List<Tool>) Json.load(args.getBiotools(), QueryType.biotools, 0, args.getFetcherPrivateArgs().getUserAgent());

		Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				Path tempFile = null;
				try {
					Path biotoolsPath = Paths.get(args.getBiotools());
					tempFile = Files.createTempFile(biotoolsPath.getParent() != null ? biotoolsPath.getParent() : Paths.get("."), "biotools-", ".tmp");
					getBiotools(tempFile.toString());
					biotools = (List<Tool>) Json.load(tempFile.toString(), QueryType.biotools, 0, args.getFetcherPrivateArgs().getUserAgent());
					Files.copy(tempFile, biotoolsPath, StandardCopyOption.REPLACE_EXISTING);
				} catch (Throwable e) {
					logger.error("Exception!", e);
				} finally {
					if (tempFile != null) {
						try {
							Files.delete(tempFile);
						} catch (Throwable e) {
							logger.error("Exception!", e);
						}
					}
				}
			}
		}, BIOTOOLS_FILE_INITIAL_DELAY, BIOTOOLS_FILE_DELAY, TimeUnit.HOURS);

		org.edamontology.edammap.server.Server.run("tools.bio.pub2tools.server", version, "pub2tools", args.getServerPrivateArgs(), args.getServerPrivateArgs().getPath(), args.getLog(), Resource::runGet, processor, logger);
	}

	public static void main(String[] argv) throws IOException, ReflectiveOperationException {
		version = new Version(Server.class);

		args = BasicArgs.parseArgs(argv, ServerArgs.class, version, false);

		// logger must be called only after configuration changes have been made in BasicArgs.parseArgs()
		// otherwise invalid.log will be created if arg --log is null
		logger = LogManager.getLogger();
		logger.debug(String.join(" ", argv));
		logger.info("This is {} {} ({})", version.getName(), version.getVersion(), version.getUrl());

		try {
			run();
		} catch (Throwable e) {
			logger.error("Exception!", e);
		}
	}
}
