/*
 * Copyright © 2018, 2019 Erik Jaaniso
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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import org.edamontology.pubfetcher.core.common.BasicArgs;
import org.edamontology.pubfetcher.core.common.FetcherArgs;

import org.edamontology.edammap.core.mapping.args.MapperArgs;
import org.edamontology.edammap.core.preprocessing.PreProcessorArgs;

public class Pub2ToolsArgs extends BasicArgs {

	@Parameter(names = { "-copy-edam" }, description = "Copy the EDAM ontology file in OWL format given with the --edam parameter to EDAM.owl in the given output directory")
	String copyEdam = null;

	@Parameter(names = { "-copy-idf" }, description = "Copy two IDF files (one where words are stemmed and another where they are not) given with the --idf and -idf-stemmed parameters to tf.idf and tf.stemmed.idf in the given output directory")
	String copyIdf = null;

	@Parameter(names = { "-get-biotools" }, description = "Fetch the entire current bio.tools content using the bio.tools API to the file biotools.json in the given output directory")
	String getBiotools = null;

	@Parameter(names = { "-copy-biotools" }, description = "Copy the file containing bio.tools content in JSON format given with the --biotools parameter to the file biotools.json in the given output directory")
	String copyBiotools = null;

	@Parameter(names = { "-select-pub" }, description = "Fetch publication IDs of journal articles from the given period (given with --from/--to or --month or --day) that are potentially suitable for bio.tools to the file pub.txt in the given output directory")
	String selectPub = null;

	@Parameter(names = { "-copy-pub" }, description = "Copy the file containing publication IDs to download with -fetch-pub from the path given with the --pub parameter to the file pub.txt in the given output directory")
	String copyPub = null;

	@Parameter(names = { "-init-db" }, description = "Initialise an empty PubFetcher database to the file db.db in the given output directory")
	String initDb = null;

	@Parameter(names = { "-copy-db" }, description = "Copy the PubFetcher database given with the --db parameter to the file db.db in the given output directory")
	String copyDb = null;

	@Parameter(names = { "-fetch-pub" }, description = "This will fetch publications for publication IDs given in pub.txt to the database file db.db in the given output directory")
	String fetchPub = null;

	@Parameter(names = { "-pass1" }, description = "The first pass of the Pub2Tools algorithm will load all publications from db.db corresponding to the publication IDs in pub.txt and write found tool names with scores and other data to pass1.json, with related links extracted from publication abstracts and fulltexts written to web.txt and doc.txt in the given output directory")
	String pass1 = null;

	@Parameter(names = { "-fetch-web" }, description = "This will fetch webpages and docs for URLs given in web.txt and doc.txt to the database file db.db in the given output directory")
	String fetchWeb = null;

	@Parameter(names = { "-pass2" }, description = "The second pass of the Pub2Tools algorithm will load all results of the first pass from pass1.json and after iterating over these, while processing them and calculating new results, it will write the final results to results.csv, new.json and diff.csv in the given output directory")
	String pass2 = null;

	@Parameter(names = { "-map", "-edammap" }, description = "This step will add EDAM ontology annotations using EDAMmap to the Pub2Tools results in new.json, outputting the annotated results to to_biotools.json in the given output directory")
	String map = null;

	@Parameter(names = { "-all" }, description = "This command will run all setup commands necessary for getting the files required by the steps and then run all steps in order (-fetch-pub, -pass1, -fetch-web, -pass2, -map). Required arguments for the setup commands are --edam, --idf, --idf-stemmed and either --from/--to or --month or --day or --pub. Arguments --biotools and --db can also be specified.")
	String all = null;

	@Parameter(names = { "-resume" }, description = "This command will run all steps starting with the step stored in step.txt until the last step (-map)")
	String resume = null;

	@Parameter(names = { "--edam", "--edam-owl" }, description = "The EDAM ontology OWL file to be copied to the output directory with -copy-edam (or -all)")
	String edam = null;

	@Parameter(names = { "--idf", "--query-idf" }, description = "The unstemmed IDF file to be copied to the output directory with -copy-idf (or -all)")
	String idf = null;

	@Parameter(names = { "--idf-stemmed", "--query-idf-stemmed" }, description = "The stemmed IDF file to be copied to the output directory with -copy-idf (or -all)")
	String idfStemmed = null;

	@Parameter(names = { "--biotools", "--biotools-json" }, description = "The JSON file containing the entire bio.tools content to be copied to the output directory with -copy-biotools (or -all)")
	String biotools = null;

	@Parameter(names = { "--from", "--from-date" }, description = "The start date (in the form 2019-08-23) of the date range used to fetch publication IDs from with -select-pub (or -all)")
	String from = null;

	@Parameter(names = { "--to", "--to-date" }, description = "The end date (in the form 2019-08-23) of the date range used to fetch publication IDs from with -select-pub (or -all)")
	String to = null;

	@Parameter(names = { "--month" }, description = "One month (in the form 2019-08) for which publication IDs should be fetched from with -select-pub (or -all)")
	String month = null;

	@Parameter(names = { "--day" }, description = "One day (in the form 2019-08-23) for which publication IDs should be fetched from with -select-pub (or -all)")
	String day = null;

	@Parameter(names = { "--pub", "--pub-ids", "--pub-file" }, description = "The file containing publication IDs to be copied to the output directory with -copy-pub (or -all)")
	String pub = null;

	@Parameter(names = { "--db", "--database" }, description = "The PubFetcher database file to be copied to the output directory with -copy-db (or -all)")
	String db = null;

	@Parameter(names = { "--fetcher-threads", "--fetch-threads" }, description = "Number of threads to use for parallel fetching in -fetch-pub and -fetch-web (or -all or -resume)")
	int fetcherThreads = 8;

	@Parameter(names = { "--mapper-threads", "--map-threads" }, description = "Number of threads to use for parallel mapping in -map (or -all or -resume)")
	int mapperThreads = 4;

	@Parameter(names = { "--verbose" }, description = "The level of log messages that code called from PubFetcher (like fetching publications and web pages) and EDAMmap (like progress of mapping) can output to the console. For example, a value of WARN would enable printing of ERROR and WARN level log messages from PubFetcher and EDAMmap code. To note, this affects only log messages output to the console, as log messages of any level from PubFetcher and EDAMmap code are written to the log file in any case.")
	LogLevel verbose = LogLevel.OFF;

	@Parameter(names = { "-before-after" }, description = "Generate a list of words that should increase the score of a suggestion, when one of these words occurs before or after that suggestion. Words that should increase the score more are output more in front. The presumable suggestion is taken from the tool_title, if one exists. Required args: --idf, --pub, --db.")
	boolean beforeAfter = false;

	@Parameter(names = { "-europepmc-abstract" }, description = "Calculate how frequent a token is in an abstract about a tool vs in all possible abstracts from Europe PMC (for all tokens that occur at least the given number of times). Required args: --db (containing all publications of bio.tools).")
	Integer europepmcAbstract = null;

	@ParametersDelegate
	PreProcessorArgs preProcessorArgs = new PreProcessorArgs();

	@ParametersDelegate
	FetcherArgs fetcherArgs = new FetcherArgs();

	@ParametersDelegate
	MapperArgs mapperArgs = new MapperArgs();
}
