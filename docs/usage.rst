
############
Usage manual
############


After Pub2Tools is `installed <https://github.com/bio-tools/pub2tools/blob/master/INSTALL.md>`_, it can be executed on the command line by running the ``java`` command (from a Java Runtime Environment (JRE) capable of running at least version 8 of Java), while giving the compiled Pub2Tools .jar file as argument. For example, executing Pub2Tools with the argument ``-h`` or ``--help`` outputs a list of all possible parameters and commands:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar --help

Running Pub2Tools consists of running it multiple times with different `Setup commands`_ that copy or fetch files required as prerequisites for the Steps_ commands, which, after being all run, will generate the end results. Commands of Pub2Tools begin with one dash (``-``) and parameters_ giving required arguments to or influencing the commands begin with two dashes (``--``). All commands must be followed by the :ref:`output directory <output_directory>` path where all files of a Pub2Tools run will end up.


.. _setup_commands:

**************
Setup commands
**************

Setup commands can be run in any order, and also multiple times -- previous files will just be overwritten. Setup commands must be run such that all files required for the Steps_ are present in the :ref:`output directory <output_directory>`: :ref:`EDAM.owl <edam_owl>`, :ref:`tf.idf <tf_idf>`, :ref:`tf.stemmed.idf <tf_stemmed_idf>`, :ref:`biotools.json <biotools_json>`, :ref:`pub.txt <pub_txt>` and :ref:`db.db <db_db>`.

.. note::
  Once any Steps_ has completed successfully, no Setup commands can be run anymore and only Steps commands are allowed to be run to finalise the current Pub2Tools run.

.. _copy_edam:

-copy-edam
==========

Copy the `EDAM ontology <http://edamontology.org/page>`_ file in OWL format given with the ``--edam`` parameter to :ref:`EDAM.owl <edam_owl>` in the given output directory. The EDAM ontology is used in the last `-map`_ step to add EDAM annotations to the results.

The given OWL file can be a path to a file in the local file system or a web link, in which case the file will be downloaded from the link to the given output directory. Fetching of the link can be influenced by the `-\\-timeout <https://pubfetcher.readthedocs.io/en/latest/cli.html#timeout>`_ and `-\\-user-agent <https://pubfetcher.readthedocs.io/en/latest/cli.html#useragent>`_ parameters.

.. note::
  In the same way, either a local file or a web resource can be given as the source file for all following ``-copy`` commands.

Examples copying the EDAM ontology file to the directory ``results``:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -copy-edam results --edam path/to/EDAM.owl
  $ java -jar path/to/pub2tools-<version>.jar -copy-edam results --edam http://edamontology.org/EDAM.owl

.. _copy_idf:

-copy-idf
=========

Copy two IDF files (one where words are stemmed and another where they are not) given with the ``--idf`` and ``-idf-stemmed`` parameters to :ref:`tf.idf <tf_idf>` and :ref:`tf.stemmed.idf <tf_stemmed_idf>` in the given output directory. `tf–idf <https://en.wikipedia.org/wiki/Tf%E2%80%93idf>`_ weighting is used in multiple parts of Pub2Tools: the unstemmed version (``tf.idf``) is used in `-pass1`_ and `-pass2`_ and in the `-map`_ step either ``tf.idf`` or ``tf.stemmed.idf`` is used, depending on the used parameters (using stemming is the default).

Pre-generated IDF files are provided in the EDAMmap repo: `biotools.idf <https://github.com/edamontology/edammap/blob/master/doc/biotools.idf>`_ and `biotools.stemmed.idf <https://github.com/edamontology/edammap/blob/master/doc/biotools.stemmed.idf>`_. However, these files can also be generated from scratch using EDAMmap: more info in the `IDF section <https://edammap.readthedocs.io/en/latest/manual.html#idf>`_ of the EDAMmap manual.

Example copying the (either downloaded or generated) IDF files from their location on local disk to the ``results`` directory:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -copy-idf results --idf path/to/tf.idf --idf-stemmed path/to/tf.stemmed.idf

.. _get_biotools:

-get-biotools
=============

Fetch the entire current `bio.tools <https://bio.tools/>`_ content using the `bio.tools API <https://biotools.readthedocs.io/en/latest/api_reference.html>`_ to the file :ref:`biotools.json <biotools_json>` in the given output directory. This file containing existing bio.tools content is used in the `-pass2`_ step to see which results are already present in bio.tools.

Calls the same code as the `EDAMmap-Util <https://edammap.readthedocs.io/en/latest/manual.html#edammap-util>`_ command ``-biotools-full``.

Example fetching bio.tools content to the ``results`` directory:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -get-biotools results

.. _copy_biotools:

-copy-biotools
==============

Copy the file containing `bio.tools`_ content in JSON format given with the ``--biotools`` parameter to the file :ref:`biotools.json <biotools_json>` in the given output directory. This ``-copy`` command can be used instead of `-get-biotools`_ to re-use a biotools.json file downloaded with ``-get-biotools`` as part of some previous Pub2Tools run or to use some alternative JSON file of bio.tools entries.

Example copying bio.tools content to the ``results`` directory:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -copy-biotools results --biotools path/to/biotools.json

.. _select_pub:

-select-pub
===========

Fetch `publication IDs <https://pubfetcher.readthedocs.io/en/latest/output.html#ids-of-publications>`_ of journal articles from the given period that are potentially suitable for `bio.tools`_ to the file :ref:`pub.txt <pub_txt>` in the given output directory. The resulting file is used as input to the `-fetch-pub`_ step that will download the content of these publications forming the basis of the search for new tools and services to add to bio.tools. Only articles matching certain criteria are selected, as otherwise the number of publications to download for the given period would be too large -- due to this filtering the number of publications is reduced by around 50 times.

The granularity of the selectable period is one day and the range can be specified with the parameters ``--from`` and ``--to``. As argument to these parameters, an ISO-8601 date must be given, e.g. ``2019-08-23``. Instead of ``--from`` and ``--to`` the parameters ``--month`` or ``--day`` can be used. The parameter ``--month`` allows to specify an exact concrete month as the period (e.g. ``2019-08``), so that the number of days in a month doesn't have to be known to cover any whole month. The parameter ``--day`` allows to specify just one whole day as the period (e.g. ``2019-08-23``).

Fetching the publication IDs works by sending large query strings to the `Europe PMC API <https://europepmc.org/RestfulWebService>`_ and extracting the PMID, PMCID and DOI from the returned results. The query string consists of phrases that must be present in the abstract (``OR``-ed together), of the content source, of the date range and of phrases that must not appear in the publication abstract or title (``AND``-ed together):

* Phrases that must appear in a publication abstract are divided into categories and by combining these categories we get the final necessary requirement a publication abstract must meet to be included in the selection. Files corresponding to these categories are the following:

  * `excellent <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/select/excellent.txt>`_, e.g. "github", "implemented in r", "freely available"
  * `good <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/select/good.txt>`_, e.g. "available for academic", "sequence annotation", "unix"
  * `mediocre1 <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/select/mediocre1.txt>`_, e.g. "our tool", "paired-end", "ontology"
  * `mediocre2 <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/select/mediocre2.txt>`_, e.g. "computationally", "high-throughput", "shiny"
  * `http <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/select/http.txt>`_, e.g. "https", "index.html"
  * `tool_good <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/select/tool_good.txt>`_, e.g. "server", "plugin"
  * `tool <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/select/tool.txt>`_, e.g. "tool", "pipeline", "repository"

  Out of these, only phrases from ``excellent`` are sufficient on their own to meet the inclusion requirement. Phrases from other categories must be combined, e.g. an abstract matching one phrase from ``good`` and another from ``http`` will also meet the requirement. This can be directly encoded as an Europe PMC query by ``AND``-ing together the ``OR``-ed phrase of ``good`` with the ``OR``-ed phrase of ``http``. However, some combinations can't be expressed as Europe PMC queries, for example one phrase from ``good``, another from ``tool`` and a third, but different one also from ``tool``. In that case, results for all phrases of ``tool`` must be fetched from Europe PMC one by one and programmatically combined in Pub2Tools. In total, the following combinations are done:

  * ``excellent``
  * ``good`` + ``http``
  * ``good`` + ``tool_good``
  * ``mediocre1`` + ``http`` + ``tool``
  * ``mediocre2`` + ``http`` + ``tool``
  * ``mediocre1`` + ``tool_good`` + ``tool``
  * ``mediocre2`` + ``tool_good`` + ``tool``
  * ``http`` + ``tool_good``
  * ``good`` + ``tool`` + ``tool``
  * ``mediocre`` + ``tool`` + ``tool`` + ``tool``
  * ``http`` + ``tool`` + ``tool``
  * ``tool_good`` + ``tool_good``
  * ``tool_good`` + ``tool`` + ``tool``

  .. note::
    The category ``mediocre`` is split into two in the implementation simply because otherwise query strings sent to Europe PMC would get too long for it to handle.
  .. note::
    It seems that common words (stop words maybe) are filtered out, e.g. ``ABSTRACT:"available as web"`` and ``ABSTRACT:"available at web"`` give the same results (however ``ABSTRACT:"available web"`` gives different results, so some stop word must be present in-between).

* Europe PMC has content from different sources (`What am I searching on Europe PMC? <https://europepmc.org/Help#whatserachingEPMC>`_). Pub2Tools searches from "MED" (PubMed/MEDLINE NLM) and "PMC" (PubMed Central). As we are only interested in the PMID and PMCID, then we request a minimal amount of information in the results to save bandwidth (``resultType=idlist``).

  But Pub2Tools also searches from the source "PPR" (Preprints). The inclusion of `preprints in Europe PMC <http://blog.europepmc.org/2018/07/preprints.html>`_ is a nice feature that enables Pub2Tools to easily extend the search of tools to publications in services like `bioRxiv <https://www.biorxiv.org/>`_ and `F1000Research <https://f1000research.com/>`_. In case of preprints we usually can only get a DOI and, as the minimal results do not contain it, we execute the query for publication IDs from preprints separately (``resultType=lite``).

* The date range is specified with ``--from`` and ``--to`` or ``--month`` or ``--day``, as explained above. The search field filled with the specified date is "CREATION_DATE", which is the first date of entry of the publication to Europe PMC. This is not necessarily equal to the (print or electronic) publication date of the journal article, as a publication can be added to Europe PMC some time after it has been published, but also ahead of publication time. The search field "CREATION_DATE" is used instead of publication date to try to ensure that the set of publications returned for some date range remains the same in different points in time. For example, if all publications of August are queried at some date in September, we would want to get more or less the same results also at some query date in October. If the article publication date was used as the search field, then maybe some articles published in August were added to Europe PMC in the meantime, meaning that the query made in October would return more results and the query made in September would miss those newly added publications. Using "CREATION_DATE" enables us to do the query of publications added to Europe PMC in August only once and not bother with that date range anymore in later queries.

* As the last part of the query string sent to Europe PMC, phrases that must not appear in the publication abstract or title are specified. These help to exclude a few publications that are otherwise selected, but that are actually not about a tool or service (mostly, publications about a medical trial and review articles are excluded this way). The exclusion phrases are specified in the files `not_abstract.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/select/not_abstract.txt>`_ (e.g. "trial registration", "http://clinicaltrials.gov") and `not_title.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/select/not_title.txt>`_ (e.g. "systematic review", "controlled trial").

Some journals have articles suitable for bio.tools more often than some other journals. As the selection of publications with phrases that must appear in the abstract is not perfect and sometimes excludes good articles, it makes sense to not use this mechanism for some high relevance journals and instead download all publication of the given period from these journals. If the number of such journals is not too high, then this does not significantly increase the total number of publications that must be downloaded. The list of such high priority journals is specified in the file `journal.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/select/journal.txt>`_. Phrase exclusion with ``not_abstract.txt`` and ``not_title.txt`` is still done.

Two equivalent examples fetching all publication IDs for the month of August 2019 to the directory ``results``:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -select-pub results --from 2019-08-01 --to 2019-08-31
  $ java -jar path/to/pub2tools-<version>.jar -select-pub results --month 2019-08

Example selecting publication IDs from publications added to Europe PMC on the 23rd of August 2019:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -select-pub results --day 2019-08-23

.. _copy_pub:

-copy-pub
=========

Copy the file containing `publication IDs`_ to download with `-fetch-pub`_ from the path given with the ``--pub`` parameter to the file :ref:`pub.txt <pub_txt>` in the given output directory. This ``-copy`` command can be used instead of `-select-pub`_ in order to use publication IDs got through some different means (for example, a list of publication IDs could be manually created).

Example copying the file containing publication IDs to the ``results`` directory:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -copy-pub results --pub path/to/pub.txt

.. _init_db:

-init-db
========

Initialise an empty `PubFetcher database <https://pubfetcher.readthedocs.io/en/latest/output.html#database>`_ to the file :ref:`db.db <db_db>` in the given output directory. The database is used to store the contents of the publications fetched with `-fetch-pub`_ and webpages and docs fetched with `-fetch-web`_. The database is read for getting the contents of publications in `-pass1`_, for the contents of webpages and docs in `-pass2`_ and for all content in `-map`_.

.. note::
  In contrast to other Setup commands, ``-init-db`` will not automatically overwrite an existing file (as the filling of an existing database file might have taken a lot of resources), so ``db.db`` must be explicitly removed by the user if ``-init-db`` is to be run a second time.

Calls the same code as the `PubFetcher CLI <https://pubfetcher.readthedocs.io/en/latest/cli.html>`_ command `-db-init <https://pubfetcher.readthedocs.io/en/latest/cli.html#database>`_.

Example initialising an empty database file to the ``results`` directory:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -init-db results

.. _copy_db:

-copy-db
========

Copy the `PubFetcher database`_ given with the ``--db`` parameter to the file :ref:`db.db <db_db>` in the given output directory. This can be used instead of `-init-db`_ in order to use a database full of publications and web pages got through some other means.

Example copying an existing database to the ``results`` directory:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -copy-db results --db path/to/db.db


.. _steps:

*****
Steps
*****

Once setup is done, steps must be run in the given order: `-fetch-pub`_, `-pass1`_, `-fetch-web`_, `-pass2`_ and `-map`_. A re-run, starting from any step, is also possible -- previous results will be overwritten. And if there is confidence in the set of publications and web pages not changing, then `-fetch-pub`_ and `-fetch-web`_ can be skipped, if they have been run at least once. Although running also `-fetch-pub`_ and `-fetch-web`_ a second time might be beneficial in that some previously inaccessible or slow web resources might now be online. After a step successfully concludes, the next step to be run is written to :ref:`step.txt <step_txt>`. Once all steps have completed successfully, the files :ref:`results.csv <results_csv>`, :ref:`diff.csv <diff_csv>` and :ref:`to_biotools.json <to_biotools_json>` will be present in the given :ref:`output directory <output_directory>`.

.. _fetch_pub:

-fetch-pub
==========

This will `fetch publications <https://pubfetcher.readthedocs.io/en/latest/fetcher.html#fetching-publications>`_ for publication IDs given in :ref:`pub.txt <pub_txt>` to the database file :ref:`db.db <db_db>` in the given output directory. Fetching is done like in the `-db-fetch-end <https://pubfetcher.readthedocs.io/en/latest/cli.html#get-content>`_ method of PubFetcher. Fetching behaviour can be influenced by the `Fetching parameters <https://pubfetcher.readthedocs.io/en/latest/cli.html#general-parameters>`_ and by ``--fetcher-threads`` that sets how many threads to use for parallel fetching (default is 8).

For best results, before a major run PubFetcher `scraping rules <https://pubfetcher.readthedocs.io/en/latest/scraping.html>`_ could be `tested <https://pubfetcher.readthedocs.io/en/latest/scraping.html#testing-of-rules>`_ with the PubFetcher CLI command ``-test-site``, especially if this hasn't been done in a while. Also for better results, ``-fetch-pub`` could potentially be run multiple times, spaced out by a few days, as some web pages might have been temporarily inaccessible the first time. A re-run is quicker as fetching is not retried for resources that were fetched to final state the first time. And also for better results, sometimes full texts of publications are downloaded directly from publisher sites, thus using Pub2Tools in a network with better access to those is beneficiary.

Example of running the step with some non-default parameter values:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -fetch-pub results --timeout 30000 --journalsYaml fixes.yaml --fetcher-threads 16

.. _pass1:

-pass1
======

The first pass of the Pub2Tools algorithm will load all `publications <https://pubfetcher.readthedocs.io/en/latest/output.html#content-of-publications>`_ from :ref:`db.db <db_db>` corresponding to the publication IDs in :ref:`pub.txt <pub_txt>` and iterate over these publications trying to find a **name** for the tool or service each publication is potentially about, assign a goodness **score** to the name suggestion and try to find web **links** of the tool from the publication abstract and full text. The unstemmed :ref:`tf.idf <tf_idf>` is also read, as `tf–idf`_ weighting is used as part of the scoring and link matching. Results are output to :ref:`pass1.json <pass1_json>` (for input to the second pass `-pass2`_) and matched links to :ref:`web.txt <web_txt>` and :ref:`doc.txt <doc_txt>` (so that `-fetch-web`_ can download their contents).

First, text from publications needs to be preprocessed -- support for this comes from `EDAMmap <https://github.com/edamontology/edammap>`_. Input is tokenised and processed, for example everything is converted to lowercase and stop words are removed. Processing is good for doing comparisons etc, however tokens closer to the original form (e.g. preserving the capitalisation) are also kept, as this is what we might want to output to the user. Code to divide the input into sentences and to extract web links has also been implemented in EDAMmap and is used here. This implementation might not be perfect, but it has enabled devising regexes and hacks dealing with quirks and mistakes specific to the input got from publications. The removal of stop words and some other preprocessing (except ``--stemming``) can be influenced by the `Preprocessing parameters <https://edammap.readthedocs.io/en/latest/api.html#preprocessing>`_.

Then, the process of looking at all possible phrases in the publication title and abstract as potential names of a tool or service begins. The goodness scores of the phrases are calculated and modified along the way:

* First, words in the title and abstract are scored according to tf–idf weighting, using the ``tf.idf`` file generated from bio.tools content. A unique word (according to bio.tools content) appearing once in the abstract will get a score of 1. The more common the word is, the lower the score according to a formula. If the word occurs more than once in the title and abstract, then the score will be higher. Short phrases (many words as a tool name) are also calculated scores for, using the scores of their constituent words.

* Quite often, the tool name is present in a publication title as "Tool name: foo bar", "Tool name - a foo bar", etc. Extracting the phrase before ": ", " - ", etc, and removing some common words like "database", "software", "version", "update", etc, from that phrase would result in a phrase (the :ref:`tool_title <tool_title>`) that we have more confidence in being the tool name. Thus, we increase the score of that phrase by multiplying its score from the last step with a constant (or initialise it to a constant if the extracted phrase is a new combination). The ``tool_title`` could also be an acronym with the expanded name occurring somewhere in the abstract. Fittingly, matching acronyms to their expanded forms is also supported (here and in the next steps).

* In a publication abstract about a tool, certain words tend to occur more often just before or after the tool's name than they occur elsewhere. So, if a candidate phrase has one such word before or after it, the probability that the phrase is a tool name is higher and we can increase its score. The list of such words that often occur just before or after (or one step away) from a tool name was bootstrapped by tentatively setting as tool name the :ref:`tool_title <tool_title>` (where available). These bootstrapped words were divided into tiers based on how much they preferably occur around the ``tool_title``, thus how much they should increase the score. For example, the best words to occur before a tool name are in `before_tier1.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/pass1/before_tier1.txt>`_ (e.g. "called", "named") and after a tool name in `after_tier1.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/pass1/after_tier1.txt>`_ (e.g. "freely", "outperforms"). Words raising the score less are in `before_tier2.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/pass1/before_tier2.txt>`_ and `after_tier2.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/pass1/after_tier2.txt>`_, `before_tier3.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/pass1/before_tier3.txt>`_ and `after_tier3.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/pass1/after_tier3.txt>`_. Now, having these word lists, we can iterate through each candidate phrase in the title and abstract and raise the score by some amount depending on the tier (but up to a limit) each time when a "before" or "after" word is found to be in the neighbourhood.

* If an abstract contains web links, we can be somewhat more certain that the publication is about a software tool or service, as in such publications links to the tool are often put in the abstract. However, such links can point to other things as well, for example to some resource used in the publication. So what we would like to do, is to match these links to phrases in the abstract and increase the score of candidate phrases that have matching links. In addition to matching links in the abstract, it also makes sense to match the candidate phrases from the abstract to links in the full text of the publication (while having a smaller matching score in that case), as often the homepage of the tool is not put into the abstract or additional links can appear in the full text (the repository of the tool, some documentation links, etc). The matching of links to phrases increases the score of some phrases and thus helps in finding the most likely tool name, but in addition, once the name has been chosen, we can possibly suggest a homepage, documentation links, etc, (done in `-pass2`_) based on the links attached to the name.

  The matching of links is done by extracting the part of the link URL string that is most likely a tool or service name and matching it in various forms (including in acronym form) to the candidate phrase. The part extracted from the URL string is either a path part, or if there is no path or all path parts are too unlikely, then it is extracted from the domain name. Choosing the correct path part is done from right to left with the unlikeliness of being the tool name decided mainly by tf–idf weighting. If the name has to be extracted from the domain name, then the lowest level domain name part is chosen, unless it matches some hardcoded patterns or any of the words in `host_ignore.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/pass1/host_ignore.txt>`_ (in which case, the link can't be matched to any phrases at all).

  In some cases, the tool or service name can correctly be extracted from the link, however it doesn't match any phrases in the publication title or abstract simply because the tool name is not mentioned there. To also catch and potentially include such publications, such orphaned link parts are added to the pool of candidate phrases (:ref:`from_abstract_link <from_abstract_link>` of such name suggestions is set to ``true``). In some other cases, the matching of links fails for some other reason or the extraction of the link part fails to work correctly, so as a backup mechanism, candidate phrases are also matched to any part of a link URL (but in case of a match, the score of the phrase is not increased).

Once the final score has been calculated, candidate phrases are ordered by it and the top one suggested as the tool or service name. Up to 4 more candidates can be output, if their scores are not too low compared to the top one. This usually means that for publications where the confidence in the top choice is not very high, other options besides the top one will also appear.

The publications themselves can also be ordered based on the scores of their top choices and possibly a threshold could be drawn somewhere, below which we would say that the publication is not about a tool or service (however, such `final decision`_ will be done in the end of the second pass by taking some additional aspects into account).

.. note::
  A higher score does not mean a "better", higher impact, etc tool. It just mean that Pub2Tools is more confident about the correctness of the extracted tool name.

.. _publication_split:

.. note::
  One publication can possibly be about more than one tool. Currently we only detect this when the names of such tools are in the title and separated by "and" or "&" -- in such case we split the publication into independent results for each tool.

The final output of the first pass of Pub2Tools needs some cleaning:

* For example, there are often problems with web links: sometimes links are "glued" together and should be broken into two separate links, sometimes there seems to be garbage at the end of a link, sometimes the schema protocol string in front of the URL is truncated, etc. We can fix some such mistakes by guesswork or sometimes a problematic link in the abstract has a correct version in the full text or vice versa. After fixing the links, we also keep the unfixed versions, because they might have been correct after all (this will be known after trying to resolve the links).
* Mistakes in the source material can cause other output to be invalid also. For example, the publication DOIs sometimes contain garbage in them that causes them to be discarded.
* Even if the output seems to be correct, it has to be valid according to `biotoolsSchema <https://github.com/bio-tools/biotoolsSchema>`_, and this can cause further modifications to be made. For example, some attribute values might need to be truncated because of maximum length requirements. Or, according to biotoolsSchema, the extracted tool name can only contain Latin letters, numbers and a few punctuation symbols and thus, invalid characters are either replaced (accents, Greek letters, etc) or discarded altogether.

In the end, results are written to :ref:`pass1.json <pass1_json>` for further processing by `-pass2`_. Results contain the publication IDs and other information about the publication, like the title, possible name extracted from the title (:ref:`tool_title <tool_title>`), sentences from the abstract, journal title, publication date, citations count, corresponding authors, but also the suggested tool name (or names in case of multiple suggestions) along with the suggestion's score and links from the abstract and full text matching the name. All matched `links are divided`_ to documentation and other links (based on the URL string alone) and written to :ref:`doc.txt <doc_txt>` and :ref:`web.txt <web_txt>` for fetching by the next step.

Example of running the step:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -pass1 results

.. _fetch_web:

-fetch-web
==========

This will `fetch webpages and docs <https://pubfetcher.readthedocs.io/en/latest/fetcher.html#fetching-webpages-and-docs>`_ for URLs given in :ref:`web.txt <web_txt>` and :ref:`doc.txt <doc_txt>` to the database file :ref:`db.db <db_db>` in the given output directory. Fetching is done like in the `-db-fetch-end`_ method of PubFetcher. Fetching behaviour can be influenced by the `Fetching parameters`_ and by ``--fetcher-threads`` that sets how many threads to use for parallel fetching (default is 8).

For best results, before a major run PubFetcher `scraping rules <https://pubfetcher.readthedocs.io/en/latest/scraping.html>`_ could be `tested <https://pubfetcher.readthedocs.io/en/latest/scraping.html#testing-of-rules>`_ with the PubFetcher CLI command ``-test-webpage``, especially if this hasn't been done in a while. Also for better results, ``-fetch-web`` could potentially be run multiple times, spaced out by a few days, as some web pages might have been temporarily inaccessible the first time. A re-run is quicker as fetching is not retried for resources that were fetched to final state the first time.

Example of running the step with some non-default parameter values:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -fetch-web results --timeout 30000 --webpagesYaml fixes.yaml --fetcher-threads 16

.. _pass2:

-pass2
======

The second pass of the Pub2Tools algorithm will load all results of the first pass from :ref:`pass1.json <pass1_json>` and while iterating over these results it will: reassess and reorder entries with lower scores by calculating a **second score**; **merge entries** if they are determined to be about the same tool; look into :ref:`biotools.json <biotools_json>` to see if an entry is **already present** in `bio.tools`_; assign **types to all the links** and decide which one of them is the **homepage**. The unstemmed :ref:`tf.idf <tf_idf>` is also read, as `tf–idf`_ weighting is used as one part of calculating the second score, and :ref:`db.db <db_db>` is read to get the license, language and description candidate phrases from webpages and docs and to check if webpages and docs are `broken <https://pubfetcher.readthedocs.io/en/latest/output.html#broken>`_ and if the `final URL <https://pubfetcher.readthedocs.io/en/latest/output.html#finalurl>`_ (after redirections) is different than the initial one. Final results of all entries are output to :ref:`results.csv <results_csv>` along with intermediate results, new entries determined to be suitable for entry into bio.tools are output to :ref:`new.json <new_json>` with the output adhering to biotoolsSchema_ and differences between the content of entries determined to already exist in bio.tools and the corresponding content in bio.tools are output to :ref:`diff.csv <diff_csv>`.

  .. _usage_score2:

Calculate score2
  A second score is calculated for entries whose first score (calculated in `-pass1`_) is below 1000. This has as goal elevating entries that are quite likely about a tool but that got a low score in the first pass (showing trouble in being confident in the extracted tool name). Up to 5 tool names are suggested for an entry after the first pass with the top name being suggested as the correct one, however this top name choice can potentially change while calculating score2 for all tool name suggestions of an entry. For calculating score2, first it is set equal to the first score and then:

  .. _score2-parts:

  1. If a suggestion has matching non-broken links in the publication abstract, then increase its score2 (matching non-broken links in the fulltext also increase score2, but less). It's possible, that the top name suggestion changes after this step, as the current top name might not have matching links, but some lesser choice might.
  2. If a suggestion matches the tool name that can be extracted from the publication title (:ref:`tool_title <tool_title>`), then increase its score2 depending on how good the match is and how many words the name contains (less is better). Again, the suggestions can get reordered and the top name suggestion change.
  3. Next, increase score2 of a suggestion based on the capitalisation of the name. A mix of lower- and uppercase letters gets the highest increase and all lowercase letters the smallest, and if the name consists of many words, then the score increase is lowered. The score2 of other suggestions besides the current top one is only increased if it was already increased by any of the two previous methods.
  4. Increase score2 of a suggestion based on the average uniqueness of the words making up the tool name (calculated based on the input :ref:`tf.idf <tf_idf>` file). The score2 of other suggestions besides the current top one is only increased if it was already increased by any of the first two methods.

  .. _usage_confidence:
Determine confidence
  If the first score is at least 1000 or score2_ is more than 3000, then confidence is "high". If score2 is more than 1750 and less or equal to 3000, then confidence is "medium". If score2 is at least 1072.1 and less than or equal to 1750, then confidence is "low". If score2 is less than 1072.1, then confidence is "very low".

  .. note::
    The confidence value is more like the confidence in the correctness of the extracted tool name. Whether an entry should be considered to be about a tool and thus eligible for entry to bio.tools is not based solely on this confidence and the `final decision`_ of inclusion is done later.

  .. _merge_same_suggestions:
Merge same suggestions
  In the first pass (`-pass1`_) a few :ref:`publications were split <publication_split>`, as sometimes a publication can be about more than one tool. Conversely, different publications can also be about the same tool, so here we try to merge different entries into one entry for these kinds of publications. This merging is done, if the top name suggestions of the entries are exactly equal and the confidences are not "very low". Entries with a "very low" confidence_ are not merged and instead connected through the :ref:`same_suggestion <same_suggestions>` field.

  .. note::
    Entries with a "very low" confidence can in some occasions also be included in :ref:`new.json <new_json>`, thus the tool name is not a unique identifier there.
Find existing bio.tools entries
  If an entry is found to be existing in `bio.tools`_, then it should not be suggested as a new addition in :ref:`new.json <new_json>`. However differences with the current bio.tools content are highlighted in :ref:`diff.csv <diff_csv>`.

  .. note::
    What is meant under the current bio.tools content is not the content of bio.tools at the exact time of running ``-pass2``, but the content in the supplied :ref:`biotools.json <biotools_json>` file.

  .. _usage_existing:

  An entry is determined to be **existing in bio.tools** in the following cases:

  * Some `publication IDs`_ of the new entry are matching some publication IDs of an entry in bio.tools.
  * The name of the entry is equal or matching (ignoring version, capitalisation, symbols, etc) a name of an entry in bio.tools and some link from that entry is matching (ignoring lowest subdomain and last path) any link from that bio.tools entry. As additional requirement in this case, the confidence_ must not be "very low" and the `final decision`_ about inclusion must be positive.
  * The name of the entry is matching a name of an entry in bio.tools and also matching a credit_ (through the name, ORCID iD or e-mail) with that bio.tools entry. The confidence_ must not be "very low" and the `final decision`_ about inclusion must be positive.

  .. _divide_links:
Divide links
  The web links extracted in `-pass1`_ from publication abstracts and full texts and matching with the tool name suggestion are divided into bio.tools link groups and assigned a type. One of these links is chosen to be the tool homepage and broken links are removed.

  The link groups of bio.tools are `link <https://biotools.readthedocs.io/en/latest/curators_guide.html#link-group>`_, `download <https://biotools.readthedocs.io/en/latest/curators_guide.html#download-group>`_ and `documentation <https://biotools.readthedocs.io/en/latest/curators_guide.html#documentation-group>`_. Further inside the group, each link is assigned a type, e.g. "Mailing list", "Source code" or "Installation instructions". In Pub2Tools, the division of links and assignment of type is done solely based on matching regular expressions to the link URL string. For example, a URL ending with ``".jar"`` would be a ``download`` with type "Binary package", matching of ``"(?i)(^|[^\\p{L}])faqs?([^\\p{L}]|$)"`` would be ``documentation`` with type "FAQ" and matching of the host ``"github.com"`` would be a ``link`` with type "Repository". Note, that some other link types might also have the host ``"github.com"``, for example the GitHub tab "Issues" is put under ``link`` type "Issue tracker", the GitHub tab "Wiki" is put under ``documentation`` type "Manual" and "Releases" is put under ``download`` type "Source package" -- so these options would have to be explored before ``link`` type "Repository". Links whose URL can't be matched by any of the rules will end up under ``link`` type "Other".

  After division, links determined to be broken_ (i.e. that were not successfully resolved or returned a non-successful HTTP status code) are removed from ``link``, ``download`` and ``documentation``.

  In the end, one of the links is chosen as the bio.tools `homepage <https://biotools.readthedocs.io/en/latest/curators_guide.html#homepage>`_. First, only links in the abstract are considered with the following priority:

  1. ``link`` "Other"
  2. ``link`` "Repository"
  3. ``documentation`` "General"
  4. other ``documentation``
  5. any non-broken link whose URL does not end with an extension suggesting it is a downloadable file

  If no suitable homepages are found this way, then links in the fulltext are considered following the same logic. If a homepage is still not found, then broken links in the abstract or fulltext are also allowed to be the homepage (and the status is set to :ref:`"broken" <homepage_broken>`, if such a homepage is found). And if there are still no suitable homepages, then a link to the publication (in PubMed, in PubMed Central or a DOI link) is set as the homepage (and the status is set to :ref:`"missing" <homepage_missing>`).

_`Description`
  .. _usage_description:

  As the bio.tools `description attribute <https://biotools.readthedocs.io/en/latest/curators_guide.html#description>`_ needs to be filled, then from all fetched and gathered content some candidate phrases are generated, that the curator can choose from or combine into the final description. The more difficult task of automatic text summarisation has not been undertaken, thus the ``description`` is an output of Pub2Tools that definitely needs further manual curation (along with EDAM terms output in `-map`_).

  As the length of the ``description`` is limited to 1000 characters, then phrases from different sources need to be prioritised and only some can be chosen for consideration. First, the publication title is output as a potential description (with the potential tool name, i.e. :ref:`tool_title <tool_title>`, removed from it).

  Then, a suitable description phrase is looked for in web page titles. From these titles, any irrelevant content and the tool name are potentially removed with simple heuristics and a minimum length is required for such modified titles to be considered. The next priority goes to the first few sentences of a web page that are long enough. But one or two sentence (one short and one longer) that contain the tool name are also looked for in any case, with the priority of such sentences depending on how far from the top of the page they are. Also, the priority of sentences from some web page are a bit higher if `scraping rules`_ for that web page exist in PubFetcher. In case of equal priority, phrases from the homepage are preferred (then from certain ``link`` types and then from ``documentation``). Menus, headers and other non-main content of web pages are mostly ignored due to PubFetcher's ability to filter out such content. Deduplication of the final suggested phrases is also attempted.

  If the search for potential phrases from web pages does not yield any results, then the description candidate phrases after the publication title will be from the publication abstract.

  In addition to the Pub2Tools results themselves, we might want to communicate to the curator things that should potentially be checked or kept in mind when curating the entry (for example, that the homepage is potentially :ref:`"broken" <homepage_broken>` or that there is a slight chance that the entry is already `existing in bio.tools`_). As there are no nice and non-hidden places for such messages in the output meant for bio.tools, then the ``description`` attribute is abused for such purpose. The messages to the curator, if any, will be appended to the description candidate phrases, are separated by ``"\n\n"``, prefixed with ``"|||"`` and written in all caps. The space reserved for the messages is up to half of ``description`` (500 characters), any non-fitting discarded messages will be logged. The list of possible messages can be seen in the :ref:`output <Output>` documentation at :ref:`description <output_description>`.

  .. _usage_license:
License
  License information for the bio.tools `license attribute <https://biotools.readthedocs.io/en/latest/curators_guide.html#license>`_ is looked for in the ``homepage`` and in the ``link``, ``download``, ``documentation`` links (in the `license field <https://pubfetcher.readthedocs.io/en/latest/output.html#license>`_ provided by PubFetcher) and also in the publication abstract. The most frequently encountered license is chosen as the suggested license.

  An encountered license string must be mapped to the SPDX inspired enumeration used in bio.tools (see `license.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/pass2/license.txt>`_). Difficulties in doing so include the fact, that many licenses have versions (which can be specified in different ways, like "GPL-3", "GPL(>= 3)" or "GPLv3", or not specified at all) and even the license name can be specified differently (as an acronym or fully spelled out or something in-between or there are just different ways to mean the same license). In free text, like the publication abstract, some license strings from the enumeration can sometimes match words in the text that are actually not about a license, so require the presence of ``"Licen[sc]"`` in the immediate neighbourhood of such matches to avoid false positives.

  .. _usage_language:
Language
  Language information for the bio.tools `language attribute <https://biotools.readthedocs.io/en/latest/curators_guide.html#programming-language>`_ is looked for in the ``homepage`` and in the ``link``, ``download``, ``documentation`` links (in the `language field <https://pubfetcher.readthedocs.io/en/latest/output.html#language>`_ provided by PubFetcher) and also in the publication abstract. All encountered languages (with duplicates removed) are chosen as the suggested languages.

  An encountered language string must be mapped to the programming language enumeration used in bio.tools (see `language.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/pass2/language.txt>`_). Most language strings are rather unique, so these can relatively safely be matched by carefully extracting the characters from the target text. However, some languages (like "C", "R", "Scheme") can easily be mistaken for other things, so the presence of a keyword (like "implemented", "software", "language", full list in `language_keywords.txt <https://github.com/bio-tools/pub2tools/blob/master/src/main/resources/pass2/language_keywords.txt>`_) in the immediate neighbourhood is required in such cases. Somewhat conversely, some words can automatically infer a language (like "bioconductor" -> "R", "django" -> "Python").

  .. _usage_credit:
Credit
  Currently, information to add to the bio.tools `credit attribute <https://biotools.readthedocs.io/en/latest/curators_guide.html#credit-group>`_ comes only from one place: the corresponding authors of publications. Corresponding authors can be extracted from articles in PubMed Central, i.e. for publications that have a PMCID, or names and e-mails can also be extracted from many journal web pages of articles, i.e. from web pages got after resolving the DOIs. The task of extracting corresponding author information is done by code from PubFetcher and stored in the publication field `correspAuthor <https://pubfetcher.readthedocs.io/en/latest/output.html#correspauthor>`_. We can possibly get the name, ORCID iD, e-mail, phone and web page of a corresponding author with PubFetcher. The only thing we can additionally do here, is merge potential duplicate authors coming from different sources (and there are some things to keep in mind when merging, for example the name of the same person can be written with or without potentially abbreviated middle names, academic titles, accents, etc).

  .. _final_decision:
Final decision
  The final decision whether an entry is suggested for entry to bio.tools (when it's determined to not already `exist in bio.tools`_) is not based solely on confidence_, but there are some further considerations. First, if confidence is "high", then it is suggested for entry. For any lower confidence, it is suggested for entry if the first score (not score2_) is high enough (the threshold depending on the exact confidence) or in case the first score is too low, then 1 or 2 (depending on score) of the following has to hold:

  1. the homepage is not :ref:`"missing" <homepage_missing>`;
  2. a license_ is found;
  3. at least one language_ is found;
  4. none of the publications have a PMID or PMCID (i.e. all publications have only a DOI).

  In addition, certain homepage suggestions (like "clinicaltrials.gov") or journals (like "Systematic reviews") will also bar the entry from being suggested (even if confidence is "high"). Also, the presence of words from `not_abstract.txt`_ in the abstract or words from `not_title.txt`_ in the publication title will exclude the entry (although, publications containing these words were already excluded by `-select-pub`_, if the normal workflow was used).

All entries (irrespective of the `final decision`_ on whether the entry should be added to bio.tools) are output to :ref:`results.csv <results_csv>` along with all possible data (explained in :ref:`results.csv columns <results_csv_columns>`). `Merged entries`_ are output as one entry, with values of the constituent entries separated by " | " in the field values.

If an entry is determined to be about a tool already `existing in bio.tools`_, then it is not suggested for entry to bio.tools. However, if there are any differences between values of the entry and values of the corresponding entry in bio.tools or if bio.tools seems to be missing information, then these differences and potential extra information are added to :ref:`diff.csv <diff_csv>` (with a detailed explanation of the values in :ref:`diff.csv columns <diff_csv_columns>`).

.. note::
  Sometimes, an entry seems to be existing in bio.tools, but the evidence for existence is not enough (and more often than not misleading) -- in that case the new entry is still suggested for entry to bio.tools, but information about the potentially related existing entry in bio.tools is output as a message in the description_.

All entries for which the `final decision`_ is positive and that are determined to not be `existing in bio.tools`_ are added to :ref:`new.json <new_json>` in biotoolsSchema_ compatible JSON. Attributes that can be filled are:

* ``name``, as extracted by `-pass1`_
* ``publication``, originally selected in `-select-pub`_
* ``description``, ``license``, ``language``, ``credit``, constructed here in the second pass (explained in description_, license_, language_, credit_)
* ``homepage``, ``link``, ``download``, ``documentation``, as described in `divide links`_

Example of running the step:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -pass2 results

.. _map:

-map
====

This step will add `EDAM ontology`_ annotations using EDAMmap_ to the Pub2Tools results in :ref:`new.json <new_json>`, outputting the annotated results to :ref:`to_biotools.json <to_biotools_json>` in the given :ref:`output directory <output_directory>`. Additional inputs to the mapping algorithm are the EDAM ontology file :ref:`EDAM.owl <edam_owl>`, the database file :ref:`db.db <db_db>` containing the contents of publications, webpages and docs, and the IDF files :ref:`tf.idf <tf_idf>` and :ref:`tf.stemmed.idf <tf_stemmed_idf>` (which IDF file being used depending on the supplied ``--stemming`` parameter, with the stemmed version being the default). As additional output, more details about the mapping results are provided in different formats and detail level in :ref:`map.txt <map_txt>`, :ref:`map/ directory of HTML files <map_dir>` and :ref:`map.json <map_json>`.

The input and output file names of the mapping step are fixed and cannot be changed. However, other aspects of the mapping process can be influenced by a multitude of parameters: `Preprocessing parameters`_, `Fetching parameters`_ and `Mapping parameters <https://edammap.readthedocs.io/en/latest/api.html#mapping>`_. By default, the default parameter values are used, for example up to 5 terms from the "topic" and "operation" branches are output (with results from the "data" and "format" branches being omitted by default, as currently EDAMmap does not work well for "data" and "format"). In addition, the parameter ``--mapper-threads`` can be used to set how many threads to use for parallel mapping of entries (default is 4).

The mapping step will in essence just fill and add the `operation attribute <https://biotools.readthedocs.io/en/latest/curators_guide.html#operation>`_ (under a new `function group <https://biotools.readthedocs.io/en/latest/curators_guide.html#function-group>`_) and the `topic attribute <https://biotools.readthedocs.io/en/latest/curators_guide.html#topic>`_, containing a list of EDAM term URIs and labels, to the new bio.tools entries in :ref:`new.json <new_json>`, outputting the result to :ref:`to_biotools.json <to_biotools_json>`.

Annotations from the "data" and "format" branches can also be added if requested. However, in the `function group`_ of bio.tools, it has to be specified whether the `data attribute <https://biotools.readthedocs.io/en/latest/curators_guide.html#data-type-input-and-output-data>`_ and the `format attribute <https://biotools.readthedocs.io/en/latest/curators_guide.html#data-format-input-and-output-data>`_ are input or output data and format, and EDAMmap can't do that. So, if mapping results for the "data" and "format" branches are to be output, they will be output to the `function note attribute <https://biotools.readthedocs.io/en/latest/curators_guide.html#note-function>`_ as text and not to the data and format attributes.

In addition to the description_ attribute, the EDAM terms output by Pub2Tools are attributes that definitely need further manual curation. Unfortunately, EDAMmap scores of the found terms cannot be output to :ref:`to_biotools.json <to_biotools_json>`, which makes it a bit harder to decide on the correctness of the suggested terms. However, suggested concepts are ordered by score so it can be assumed, that the few last term suggestions are more probably wrong. And if needed, more detailed mapping results (including scores) can be found in :ref:`map.txt <map_txt>`, :ref:`map/ directory of HTML files <map_dir>` or :ref:`map.json <map_json>`.

Example of running the step with some non-default parameter values:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -map results --stemming false --branches topic operation data format --mapper-threads 8

.. _all:

-all
====

This command will run all `setup commands`_ necessary for getting the files required by the steps_ and then run all steps in order (`-fetch-pub`_, `-pass1`_, `-fetch-web`_, `-pass2`_, `-map`_). So in essence, it could the sole command run to get a batch of new results from Pub2Tools.

The command has a few mandatory parameters: ``--edam`` to copy the EDAM ontology (as in `-copy-edam`_), ``--idf`` and ``--idf-stemmed`` to copy the IDF files (as in `-copy-idf`_) and ``--from``/``--to`` or ``--month`` or ``--day`` to specify a date range for fetching publication IDs (as in `-select-pub`_). The last date range parameters can actually be replaced with ``--pub``, that is, instead of fetching new publication IDs, a file containing publication IDs can be copied (as in `-copy-pub`_).

In addition, the parameter ``--biotools`` can be specified to copy a JSON file containing the entire content of bio.tools (as in `-copy-biotools`_) and the parameter ``--db`` can be used to copy a PubFetcher database (as in `-copy-db`_). If these parameters are omitted, then the entire current bio.tools content is fetched as part of the command (as in `-get-biotools`_) and an empty PubFetcher database is initialised (as in `-init-db`_).

All parameters (like ``--timeout``, ``--fetcher-threads``, ``--matches``) influencing the step commands can also be specified and are passed on to the steps accepting them.

An example of running the command:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -all results --edam http://edamontology.org/EDAM.owl --idf https://github.com/edamontology/edammap/raw/master/doc/biotools.idf --idf-stemmed https://github.com/edamontology/edammap/raw/master/doc/biotools.stemmed.idf --month 2019-08

.. _resume:

-resume
=======

This command will run all steps starting with the step stored in :ref:`step.txt <step_txt>` until the last step (`-map`_). Setup must have been completed separately beforehand. If ``step.txt`` is missing, then the command will run all steps, starting with `-fetch-pub`_.

In general, after a step is successfully completed, the next step is written to ``step.txt``. Thus, given some :ref:`output directory <output_directory>` where running Pub2Tools has been aborted (but setup has been completed), the ``-resume`` command allows finishing a Pub2Tools run, while not re-executing already done steps, with just one command.

As no `setup commands`_ are run, then there are no mandatory parameters. However, if resuming from an interrupted command, then the same parameters that were used for that command should be respecified here.

An example of running the command:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -resume results


.. _parameters:

**********
Parameters
**********

Parameters give required arguments to or influence the `setup commands`_ and steps_ and begin with two dashes (``--``). All the ``-copy`` setup commands have a mandatory parameter specifying the source of the file to be copied. The `-select-pub`_ setup command needs parameters to specify the data range for fetching publication IDs. All other parameters are optional and influence the default behaviour of the commands.

=====================  ===================  =======  ===========
Parameter              Parameter args       Default  Description
=====================  ===================  =======  ===========
``--edam``             *<file or URL>*               The EDAM ontology OWL file to be copied to the output directory with `-copy-edam`_ (or `-all`_)
``--idf``              *<file or URL>*               The unstemmed IDF file to be copied to the output directory with `-copy-idf`_ (or `-all`_)
``--idf-stemmed``      *<file or URL>*               The stemmed IDF file to be copied to the output directory with `-copy-idf`_ (or `-all`_)
``--biotools``         *<file or URL>*               The JSON file containing the entire bio.tools content to be copied to the output directory with `-copy-biotools`_ (or `-all`_)
``--from``             *<ISO-8601 date>*             The start date (in the form ``2019-08-23``) of the date range used to fetch publication IDs from with `-select-pub`_ (or `-all`_)
``--to``               *<ISO-8601 date>*             The end date (in the form ``2019-08-23``) of the date range used to fetch publication IDs from with `-select-pub`_ (or `-all`_)
``--month``            *<ISO-8601 month>*            One month (in the form ``2019-08``) for which publication IDs should be fetched from with `-select-pub`_ (or `-all`_)
``--day``              *<ISO-8601 date>*             One day (in the form ``2019-08-23``) for which publication IDs should be fetched from with `-select-pub`_ (or `-all`_)
``--pub``              *<file or URL>*               The file containing publication IDs to be copied to the output directory with `-copy-pub`_ (or `-all`_)
``--db``               *<file or URL>*               The PubFetcher database file to be copied to the output directory with `-copy-db`_ (or `-all`_)
``--fetcher-threads``  *<integer>*          ``8``    Number of threads to use for parallel fetching in `-fetch-pub`_ and `-fetch-web`_ (or `-all`_ or `-resume`_)
``--mapper-threads``   *<integer>*          ``4``    Number of threads to use for parallel mapping in `-map`_ (or `-all`_ or `-resume`_)
``--verbose``          *<LogLevel>*         ``OFF``  The level of log messages that code called from PubFetcher (like fetching publications and web pages) and EDAMmap (like progress of mapping) can output to the console. For example, a value of ``WARN`` would enable printing of ``ERROR`` and ``WARN`` level log messages from PubFetcher and EDAMmap code. Possible values are ``OFF``, ``ERROR``, ``WARN``, ``INFO``, ``DEBUG``. To note, this affects only log messages output to the console, as log messages of any level from PubFetcher and EDAMmap code are written to the :ref:`log file <pub2tools_log>` in any case.
=====================  ===================  =======  ===========

In addition, some commands are influenced by parameters defined in PubFetcher or EDAMmap: `Preprocessing parameters`_ (influences `-pass1`_, `-pass2`_ and `-map`_), `Fetching parameters`_ (influences `-fetch-pub`_, `-fetch-web`_, `-pass2`_ and `-map`_) and `Mapping parameters`_ (influences `-map`_).

.. note::
  The ``--stemming`` parameter in the `Preprocessing parameters`_ is always ``false`` for `-pass1`_ and `-pass2`_, but it can be set to either ``true`` or ``false`` for `-map`_ (default is ``true``).


.. _examples:

********
Examples
********

A quickstart example for August 2019, where the EDAM ontology and IDF files and the entire content of bio.tools are downloaded from the web and there are no deviations from default values in any of the steps, is the following:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -all results \
  --edam http://edamontology.org/EDAM.owl \
  --idf https://github.com/edamontology/edammap/raw/master/doc/biotools.idf \
  --idf-stemmed https://github.com/edamontology/edammap/raw/master/doc/biotools.stemmed.idf \
  --month 2019-08

The next example executes each individual setup and step command from start to final results, while changing some default values:

.. code-block:: bash

  # The EDAM ontology was previously downloaded to the local file system
  # to path/to/EDAM.owl and is copied from there to results/EDAM.owl
  $ java -jar path/to/pub2tools-<version>.jar -copy-edam results \
  --edam path/to/EDAM.owl
  # The IDF files have been downloaded to the local file system and are
  # copied from there to the output directory "results"
  $ java -jar path/to/pub2tools-<version>.jar -copy-idf results \
  --idf path/to/tf.idf --idf-stemmed path/to/tf.stemmed.idf
  # All bio.tools content is fetched to the file results/biotools.json
  $ java -jar path/to/pub2tools-<version>.jar -get-biotools results
  # Candidate publication IDs from August 2019 are fetched to the file
  # results/pub.txt
  $ java -jar path/to/pub2tools-<version>.jar -select-pub results \
  --from 2019-08-01 --to 2019-08-31
  # An empty PubFetcher database is initialised to results/db.db
  $ java -jar path/to/pub2tools-<version>.jar -init-db results
  # In the first step, the content of publications listed in
  # results/pub.txt is fetched to results/db.db, while the connect and
  # read timeout is changed to 30 seconds, some fixes for outdated
  # journal scraping rules are loaded from a YAML file and the number
  # of threads used for parallel fetching is doubled from the default 8
  $ java -jar path/to/pub2tools-<version>.jar -fetch-pub results \
  --timeout 30000 --journalsYaml journalsFixes.yaml --fetcher-threads 16
  # The first pass of Pub2Tools is run
  $ java -jar path/to/pub2tools-<version>.jar -pass1 results
  # Web pages extracted by the first pass are fetched, with some default
  # parameters modified analogously to -fetch-pub
  $ java -jar path/to/pub2tools-<version>.jar -fetch-web results \
  --timeout 30000 --webpagesYaml webpagesFixes.yaml --fetcher-threads 16
  # The second pass of Pub2Tools is run, culminating in the files
  # results/results.csv, results/diff.csv and results/new.json
  $ java -jar path/to/pub2tools-<version>.jar -pass2 results
  # EDAM annotations are added to results/new.json and output to
  # results/to_biotools.json, with stemming turned off, mapping done in
  # parallel in 8 threads and up to 5 terms output for all EDAM branches
  $ java -jar path/to/pub2tools-<version>.jar -map results \
  --stemming false --branches topic operation data format \
  --mapper-threads 8

The following example is equivalent with the previous one, just all commands have been replaced with one `-all`_ command:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -all results \
  --edam path/to/EDAM.owl --idf path/to/tf.idf \
  --idf-stemmed path/to/tf.stemmed.idf --month 2019-08 \
  --timeout 30000 --journalsYaml journalsFixes.yaml \
  --webpagesYaml webpagesFixes.yaml --fetcher-threads 16 \
  --stemming false --branches topic operation data format \
  --mapper-threads 8

All files of the setup can be obtained through some external means and simply copied to the output directory ``results``:

.. code-block:: bash

  # Copy a previously downloaded EDAM ontology to the output directory
  $ java -jar path/to/pub2tools-<version>.jar -copy-edam results \
  --edam path/to/EDAM.owl
  # Copy previously downloaded IDF files to the output directory
  $ java -jar path/to/pub2tools-<version>.jar -copy-idf results \
  --idf path/to/tf.idf --idf-stemmed path/to/tf.stemmed.idf
  # Copy the existing content of bio.tools in JSON format, obtained
  # through a different tool or through a previous run of Pub2Tools
  # to the output directory
  $ java -jar path/to/pub2tools-<version>.jar -copy-biotools results \
  --biotools path/to/biotools.json
  # Copy publication IDs obtained through some different means, for
  # example a small list of manually entered IDs meant for testing,
  # to the output directory
  $ java -jar path/to/pub2tools-<version>.jar -copy-pub results \
  --pub path/to/pub.txt
  # Copy a PubFetcher database preloaded with potentially useful
  # content to the output directory
  $ java -jar path/to/pub2tools-<version>.jar -copy-db results \
  --db path/to/db.db
  # We can use the -resume command to run all step commands in one go;
  # the number of threads has been doubled from default values and up to
  # 5 EDAM terms are output in the mapping step for the default branches
  # of "topic" and "operation"
  $ java -jar path/to/pub2tools-<version>.jar -resume results \
  --fetcher-threads 16 --mapper-threads 8

The following `-all`_ command is equivalent to the previous list of commands:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -all results \
  --edam path/to/EDAM.owl --idf path/to/tf.idf --idf-stemmed \
  path/to/tf.stemmed.idf --biotools path/to/biotools.json \
  --pub path/to/pub.txt --db path/to/db.db
  --fetcher-threads 16 --mapper-threads 8 ^C (Interrupted)
  # But for some reason, the -all command was interrupted. If this
  # happened during a step command (when all setup was already done),
  # then finishing the run can be done with the -resume command. The
  # process is resumed by restarting the step that was interrupted and
  # running the remaining steps up to the end. The same step parameters
  # that were supplied to -all must also be supplied to -resume.
  $ java -jar path/to/pub2tools-<version>.jar -resume results \
  --fetcher-threads 16 --mapper-threads 8

When fetching publications and web pages, some resources might be temporarily down. So for slightly better results, one option could be to wait a few days after an initial fetch and hope that a few extra resources would be available then. Due to PubFetcher's logic, publications and web pages that were successfully fetched in full the first time, are not retried during refetching:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -fetch-pub results
  $ java -jar path/to/pub2tools-<version>.jar -pass1 results
  $ java -jar path/to/pub2tools-<version>.jar -fetch-web results
  $ # Wait a few days
  $ java -jar path/to/pub2tools-<version>.jar -fetch-pub results
  $ java -jar path/to/pub2tools-<version>.jar -pass1 results
  $ java -jar path/to/pub2tools-<version>.jar -fetch-web results
  $ java -jar path/to/pub2tools-<version>.jar -pass2 results
  $ java -jar path/to/pub2tools-<version>.jar -map results

To run all steps again after the wait, another option would be to just use the `-resume`_ command after removing :ref:`step.txt <step_txt>`:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -fetch-pub results
  $ java -jar path/to/pub2tools-<version>.jar -pass1 results
  $ java -jar path/to/pub2tools-<version>.jar -fetch-web results
  $ # Wait a few days
  $ rm results/step.txt
  $ java -jar path/to/pub2tools-<version>.jar -resume results ^C (Interrupted)
  # The -resume command was interrupted for some reason. If -resume is
  # now run again, it will not start again from -fetch-pub, but from the
  # step that was interrupted.
  $ java -jar path/to/pub2tools-<version>.jar -resume results

.. note::
  Before a bigger run of Pub2Tools, it could be beneficial to `test if scraping rules <https://pubfetcher.readthedocs.io/en/latest/scraping.html#testing-of-rules>`_ are still up to date. The running of Pub2Tools in a network with good access to journal articles could also be beneficial, as publisher web sites have to be consulted sometimes.

.. _improving_existing:

Improving existing `bio.tools`_ entries
=======================================

When Pub2Tools is run on a relatively new batch of `publication IDs`_, then most results will end up in :ref:`to_biotools.json <to_biotools_json>` as new entry suggestions for bio.tools and only a few results will be diverted to :ref:`diff.csv <diff_csv>` as fix suggestions of existing bio.tools entries. This is expected, as most new articles will be about tools not seen before and only some will be update articles of tools already entered to bio.tools or articles of tools entered to bio.tools through some other means than Pub2Tools.

But as an alternative and additional example of Pub2Tools usage we can consider the following: let's get all publication IDs (and nothing else) currently in bio.tools and run Pub2Tools on these IDs. Now, most results will end up in :ref:`diff.csv <diff_csv>`, as all entries are determined to be already `existing in bio.tools`_. So what we get out of this, is a large spreadsheet of suggestions (``diff.csv``) on what to improve in existing bio.tools content. A lot of the suggestions are incorrect, but there should also be many valuable fixes and additions there. If content missing in bio.tools was found, then the tool will suggest adding it to bio.tools (to "publication", "link", "download", "documentation", "language" or "credit"), or for some content types, the tool will suggest modifying existing content (in "name", "homepage", "license" or "credit"). No suggestions (for removal) are given for content that is present in bio.tools, but that was not found by the tool. All values suggested by the tool are valid according to biotoolsSchema_, but they don't necessarily follow the `curation guidelines <https://biotools.readthedocs.io/en/latest/curators_guide.html>`_.

In addition, the file :ref:`to_biotools.json <to_biotools_json>` will probably not be totally empty, but contain a few suggested new entries to bio.tools -- this happens, because some publications seem to be about multiple tools and are broken up, and it would seem that some of these broken up tools are missing in bio.tools. As detecting multiple tools from one publication is relatively crude right now, then the quality of these new entries in the JSON file might not be the best, but going through them might still be worth the while.

Running Pub2Tools on existing bio.tools content can be done in the following way (needs `EDAMmap-Util <https://edammap.readthedocs.io/en/latest/manual.html#edammap-util>`_ from EDAMmap_):

.. code-block:: bash

  # Download all bio.tools content to biotools.json
  $ java -jar path/to/edammap-util-<version>.jar -biotools-full biotools.json
  # Extract all publication IDs present in biotools.json to pub.txt
  $ java -jar path/to/edammap-util-<version>.jar -pub-query biotools.json \
  --query-type biotools -txt-ids-pub pub.txt --plain
  # Run Pub2Tools with default parameters, outputting all to directory "results"
  $ java -jar path/to/pub2tools-<version>.jar -all results \
  --edam http://edamontology.org/EDAM.owl \
  --idf https://github.com/edamontology/edammap/raw/master/doc/biotools.idf \
  --idf-stemmed https://github.com/edamontology/edammap/raw/master/doc/biotools.stemmed.idf \
  --biotools biotools.json \
  --pub pub.txt

.. note::
  The example works best when still only few entries have been added to bio.tools from curated Pub2Tools results, as for entries from these results mistakes made by Pub2Tools would have to be gone through repeatedly.

..

.. _score2: usage_score2_
.. _confidence: usage_confidence_
.. _merged entries: merge_same_suggestions_
.. _exist in bio.tools: usage_existing_
.. _existing in bio.tools: usage_existing_
.. _divide links: divide_links_
.. _links are divided: divide_links_
.. _license: usage_license_
.. _language: usage_language_
.. _credit: usage_credit_
.. _final decision: final_decision_
