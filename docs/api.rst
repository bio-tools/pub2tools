
.. _api:

###
API
###

The Pub2Tools API is consumed by sending a JSON request with HTTP POST. The main endpoint is `/api`_, which on the public instance translates to https://elixir.ut.ee/pub2tools/api.

JSON numbers and booleans are converted to strings internally. JSON objects are ignored (except under `bio.tools input`_), meaning there is no hierarchy in the request JSON structure.


.. _api_endpoint:

****
/api
****

The main endpoint is used for constructing one bio.tools entry candidate based on the given input. It can execute two `steps`_. The key-value pairs in the request JSON fall under two categories: `query data`_ and parameters_.

Steps
=====

withoutmap
----------

Executes Pub2Tools and outputs tool_, which (or parts of which) can be used as input into bio.tools.

The one mandatory input is `publicationIds`_, which contains one or more articles about the tool/database for which a bio.tools entry is to be created. As an optional parameter, the `name`_ can also be specified. It is usually not needed, but it will help in the rare case, where the tool/database name in not in the abstract (and thus Pub2Tools is unable to find it), but also when Pub2Tools selects the wrong name or the wrong form of the name (capitalization, etc). The other optional parameter is `webpageUrls`_. This can help, when the wanted links are not present in the publication (abstract or full text), but also when Pub2Tools fails to associate the extracted link with the tool name (and thus does not select the link).

This step is executed by setting `step`_ to ``"withoutmap"``.

map
---

Executes `EDAMmap <https://github.com/edamontology/edammap>`_ on the ``"tool"`` output by the previous `withoutmap`_ step.

Input to this step can only be specified as `bio.tools input`_ under ``"tool"``.

This step is executed by setting `step`_ to ``"map"``.

all
---

Executes both `withoutmap`_ and `map`_ in a row.

This step is executed by setting `step`_ to ``"all"``. This is the default.

Query data
==========

The query data to be mapped can be supplied in two different ways: as strings or arrays of strings under field names `name`_, `webpageUrls`_ and `publicationIds`_ or as a `bio.tools input`_ JSON object (like a bio.tools entry in JSON format). In case data is specified using both ways, only data under the `bio.tools input`_ is used.

Pub2Tools input
---------------

The following data can be given, with only the ``"publicationIds"`` being mandatory.

=================  ========================  ===========
Key                Type                      Description
=================  ========================  ===========
_`name`            string                    Name of tool or service
_`webpageUrls`     array of strings          URLs of homepage, etc
_`publicationIds`  array of strings/objects  PMID/PMCID/DOI of journal article

                                             Note: an article ID can be specified as a string ``"<PMID>\t<PMCID>\t<DOI>"`` or as an object (the only place besides `bio.tools input`_ where a JSON object is not ignored), wherein keys ``"pmid"``, ``"pmcid"``, ``"doi"`` can be used
=================  ========================  ===========

bio.tools input
---------------

Under the field name ``"tool"``, a JSON object adhering to `biotoolsSchema <https://biotoolsschema.readthedocs.io/>`_ can be specified. All values possible in bio.tools can be specified, but only values relevant to Pub2Tools (and EDAMmap in case of the `map`_ step) will be used. A few attributes are mandatory: `name (tool) <https://biotools.readthedocs.io/en/latest/curators_guide.html#name-tool>`_, `description <https://biotools.readthedocs.io/en/latest/curators_guide.html#description>`_ and `homepage <https://biotools.readthedocs.io/en/latest/curators_guide.html#homepage>`_. In case of steps `withoutmap`_ and `all`_ also `publication group <https://biotools.readthedocs.io/en/latest/curators_guide.html#publication-group>`_ has to be non-empty. In case of the `map`_ step, the ``"tool"`` input will be mirrored under tool_ in the response_, but with found EDAM terms added to it. In case of `withoutmap`_ and `all`_, ``"tool"`` will be overwritten by the output of Pub2Tools (plus EDAMmap in case of `all`_) as tool_ in the response_.

.. _api_parameters:

Parameters
==========

Main
----

=========  ==========  ===========
Parameter  Default     Description
=========  ==========  ===========
version    ``"1"``     API version. Currently, only one possible value: ``"1"``.
_`type`    ``"core"``  Detail level of the response_. Possible values: ``"core"``, ``"full"``. Currently only detail level of EDAMmap output (step ``"map"``) is influenced.
_`step`    ``"all"``   The step to execute. Possible values: "`withoutmap`_", "`map`_", "`all`_".
=========  ==========  ===========

.. _preprocessing:

Preprocessing
-------------

See `EDAMmap API preprocessing parameters <https://edammap.readthedocs.io/en/stable/api.html#preprocessing>`_. Influences :ref:`-pass1 <pass1>`, :ref:`-pass2 <pass2>`, :ref:`-map <map>`.

.. _fetching:

Fetching
--------

The fetching parameters are implemented in `PubFetcher <https://github.com/edamontology/pubfetcher>`_ and thus are described in its documentation: `Fetching parameters <https://pubfetcher.readthedocs.io/en/stable/cli.html#fetching>`_. Influences :ref:`-fetch-pub <fetch_pub>`, :ref:`-fetch-web <fetch_web>`, :ref:`-pass2 <pass2>`, :ref:`-map <map>`.

The defaults of the following fetching parameters have been changed in Pub2Tools API: `retryLimit <https://pubfetcher.readthedocs.io/en/stable/cli.html#retrylimit>`_ from ``3`` to ``0``, `timeout <https://pubfetcher.readthedocs.io/en/stable/cli.html#timeout>`_ from ``15000`` to ``7500`` and `quick <https://pubfetcher.readthedocs.io/en/stable/cli.html#quick>`_ from ``false`` to ``true``.

.. _mapping:

Mapping
-------

See `EDAMmap API mapping parameters <https://edammap.readthedocs.io/en/stable/api.html#mapping>`_. Influences :ref:`-map <map>`.

.. _response:

Response
========

The response output can contain more or less information, depending on the specified type_ and step_. The section of most interest is probably tool_ in core_.

core
----

success
  ``true`` (if ``false``, then the JSON output of `Error handling`_ applies instead of the one below)
version
  ``"1"``
type
  ``"core"``
api
  URL of endpoint where request was sent
json
  Location of JSON results file
generator
  See `generator in EDAMmap API <https://edammap.readthedocs.io/en/stable/api.html#generator>`_
time
  See `time in EDAMmap API <https://edammap.readthedocs.io/en/stable/api.html#time>`_
query
  id
    Unique ID assigned to the query (and by extension, to this response)
  name
    Name of tool or service (as specified in `query data`_, ``null`` if not specified)
  webpageUrls
    Array of strings representing URLs of homepage, etc (as specified in `query data`_, ``null`` if not specified)
  publicationIds
    Array of objects representing IDs of journal articles (as specified in `query data`_, mandatory)

      pmid
        PMID of article
      pmcid
        PMCID of article
      doi
        DOI of article
mapping
  See `mapping in EDAMmap API <https://edammap.readthedocs.io/en/stable/api.html#mapping-output>`_

  Only present when step_ is ``"map"`` or ``"all"``
_`args`
  The Parameters_

  mainArgs
    Main parameters

    edam
      Filename of the used EDAM ontology OWL file
    biotools
      Filename of the JSON file containing existing bio.tools entries
  processorArgs
    See `processorArgs in EDAMmap API <https://edammap.readthedocs.io/en/stable/api.html#processorargs>`_
  preProcessorArgs
    Preprocessing_ parameters
  fetcherArgs
    Fetching_ parameters (implemented in PubFetcher_)
  mapperArgs
    Mapping_ parameters

    Only present when step_ is ``"map"`` or ``"all"``
_`tool`
  The bio.tools entry candidate of the tool. In case of the `map`_ step, this will have the same content as in the ``"tool"`` given as input (with ``null`` and empty values removed), but with found EDAM terms added to it. In case of `withoutmap`_ and `all`_, this will have the result of Pub2Tools as content (plus EDAM terms in case of `all`_).

  Concerning EDAM terms, EDAMmap results from the "topic" branch are added to the `topic attribute <https://biotools.readthedocs.io/en/latest/curators_guide.html#topic>`_ and results from the "operation" branch are added under a new `function group <https://biotools.readthedocs.io/en/latest/curators_guide.html#function-group>`_ object. Results from the "data" and "format" branches should be added under the ``"input"`` and ``"output"`` attributes of a function group, however EDAMmap can't differentiate between inputs and outputs. Thus, new terms from the "data" and "format" branches will be added as strings (in the form ``"EDAM URI (label)"``, separated by ``" | "``) to the `note <https://biotools.readthedocs.io/en/latest/curators_guide.html#note-function>`_ of the last function group object.
status
  Potentially useful metadata about the result of Pub2Tools, only present when step_ is ``"withoutmap"`` or ``"all"``

  score
    :ref:`score <score>`
  score2
    :ref:`score2 <score2>`
  score2Parts
    :ref:`score2_parts <score2_parts>`
  include
    :ref:`include <include>`
  existing
    :ref:`existing <existing>`
  publicationAndNameExisting
    :ref:`publication_and_name_existing <publication_and_name_existing>`
  nameExistingSomePublicationDifferent
    :ref:`name_existing_some_publication_different <name_existing_some_publication_different>`
  somePublicationExistingNameDifferent
    :ref:`some_publication_existing_name_different <some_publication_existing_name_different>`
  nameExistingPublicationDifferent
    :ref:`name_existing_publication_different <name_existing_publication_different>`
  nameMatch
    :ref:`name_match <name_match>`
  linkMatch
    :ref:`link_match <link_match>`
  nameWordMatch
    :ref:`name_word_match <name_word_match>`
  homepageBroken
    :ref:`homepage_broken <homepage_broken>`
  homepageMissing
    :ref:`homepage_missing <homepage_missing>`
  otherNames
    :ref:`other_suggestions <other_suggestions>`
  toolsExtra
    If Pub2Tools has found that the given publication(s) are about more than one tool, then the names of these extra tools (besides the primary chosen tool) are output here (along with their homepages in parenthesis, if existing)

full
----

See `full in EDAMmap API <https://edammap.readthedocs.io/en/stable/api.html#full>`_.

This extra mapping information is only present when step_ is ``"map"`` or ``"all"`` and type_ is set to ``"full"``.

.. _api_examples:

Examples
========

One way to test the API is to send JSON data using ``curl``. For example, for sending the input:

.. code-block:: json

  {
    "publicationIds": "\t\t10.1093/nar/gkad347"
  }

issue the command:

.. code-block:: bash

  $ curl -H "Content-Type: application/json" -X POST -d '{"publicationIds":"\t\t10.1093/nar/gkad347"}' https://elixir.ut.ee/pub2tools/api

In the output, results can be seen under ``"tool"``:

.. code::

  "tool" : {
    "name" : "g:Profiler",
    ...
  }

A bit longer input, also supplying a documentation URL that Pub2Tools doesn't find and asking for a bit more EDAM terms from all branches:

.. code-block:: json

  {
    "publicationIds": "\t\t10.1093/nar/gkad347",
    "webpageUrls": "https://biit.cs.ut.ee/gprofiler/page/docs",
    "branches": [ "topic", "operation", "data", "format" ],
    "matches": 6
  }

For testing, this input could be saved in a file, e.g. ``input.json``, and then the following command run:

.. code-block:: bash

  $ curl -H "Content-Type: application/json" -X POST -d '@/path/to/input.json' https://elixir.ut.ee/pub2tools/api

The same input can be broken into two steps_: first the Pub2Tools algorithm is run with ``"withoutmap"`` and then the EDAMmap algorithm is run with ``"map"``. This breaking into two steps can be useful, because both steps take time and this enables feedback already after the first step has concluded. Also, this enables manual editing of the Pub2Tools result that is fed into EDAMmap. The first step of ``"withoutmap"`` is then:

.. code-block:: json

  {
    "step": "withoutmap",
    "publicationIds": "\t\t10.1093/nar/gkad347",
    "webpageUrls": "https://biit.cs.ut.ee/gprofiler/page/docs"
  }

And then the output ``"tool"`` from the first step (after ``"description"`` is manually edited) can be copied into the second step of ``"map"`` as:

.. code-block:: json

  {
    "step": "map",
    "tool" : {
      "name" : "g:Profiler",
      "description" : "a web server for functional enrichment analysis and conversions of gene lists",
      "homepage" : "https://biit.cs.ut.ee/gprofiler",
      "documentation" : [ {
        "url" : "https://biit.cs.ut.ee/gprofiler/page/docs",
        "type" : [ "User manual" ]
      } ],
      "publication" : [ {
        "doi" : "10.1093/NAR/GKAD347",
        "pmid" : "37144459",
        "pmcid" : "PMC10320099"
      } ],
      "credit" : [ {
        "name" : "Hedi Peterson",
        "email" : "hedi.peterson@ut.ee",
        "orcidid" : "https://orcid.org/0000-0001-9951-5116",
        "typeEntity" : "Person"
      } ],
      "confidence_flag" : "high"
    },
    "branches": [ "topic", "operation", "data", "format" ],
    "matches": 6
  }


.. _prefetching:

***********
Prefetching
***********

See `prefetching in EDAMmap API <https://edammap.readthedocs.io/en/stable/api.html#prefetching>`_ (and replace the edammap API endpoints with pub2tools ones).


.. _error_handling:

**************
Error handling
**************

See `error handling in EDAMmap API <https://edammap.readthedocs.io/en/stable/api.html#error-handling>`_  (and replace the edammap API endpoints with pub2tools ones).


.. _server:

****************
Pub2Tools-Server
****************

The Pub2Tools-Server application will run both the Pub2Tools API and a web application that functions as a frontend for the API.

All command-line arguments suppliable to a Pub2Tools server can be seen with:

.. code-block:: bash

  $ java -jar pub2tools-server-<version>.jar -h

In addition to `Processing <https://edammap.readthedocs.io/en/stable/manual.html#processing>`_ and `Fetching private <https://pubfetcher.readthedocs.io/en/stable/cli.html#fetching-private>`_ parameters, Pub2Tools-Server accepts arguments described in the following table (entries marked with * are mandatory).

=======================  ==========================  =========================  ===========
Parameter                Parameter args              Default                    Description
=======================  ==========================  =========================  ===========
``--biotools`` *         *<file path>*                                          Path of the bio.tools existing content file in JSON format; will be automatically fetched and periodically updated
``--edam`` or ``-e`` *   *<file path>*                                          Path of the EDAM ontology file
``--baseUri`` or ``-b``  *<string>*                  ``http://localhost:8080``  URI where the server will be deployed (as schema://host:port)
``--path`` or ``-p``     *<string>*                  ``/pub2tools``             Path where the server will be deployed (only one single path segment supported, prepend with '/')
``--httpsProxy``                                                                Use if we are behind a HTTPS proxy
``--files`` or ``-f`` *  *<directory path>*                                     A directory where the results will be output. It must also contain required CSS, JavaScript and font resources. Will be created, if missing.
``--fetchingThreads``    *<positive integer>*        ``8``                      How many threads to create (maximum) for fetching individual database entries of one query
=======================  ==========================  =========================  ===========

The results directory with required CSS, JavaScript and font resources will be automatically created, if a nonexistent directory path is supplied. Likewise, if ``--db`` is used to specify a nonexistent file, an initial empty `database <https://pubfetcher.readthedocs.io/en/stable/output.html#database>`_ for storing `fetched <https://pubfetcher.readthedocs.io/en/stable/fetcher.html>`_ `webpages <https://pubfetcher.readthedocs.io/en/stable/output.html#content-of-webpages>`_, `docs <https://pubfetcher.readthedocs.io/en/stable/output.html#content-of-docs>`_ and `publications <https://pubfetcher.readthedocs.io/en/stable/output.html#content-of-publications>`_ is automatically created. And if a nonexistent file is specified using ``--biotools``, the file is created and the entire content of bio.tools is downloaded to it. In any case, the file specified by ``--biotools`` is replaced with the up-to-date entire content of bio.tools every 23 hours.

Pub2Tools-Server can now be run with:

.. code-block:: bash

  $ java -jar pub2tools-server-<version>.jar -b http://127.0.0.1:8080 -p /pub2tools -e EDAM_1.25.owl -f files --fetching true --db server.db --idf biotools.idf --idfStemmed biotools.stemmed.idf --biotools biotools.json --log serverlogs

The web application can now be accessed locally at http://127.0.0.1:8080/pub2tools and the :ref:`API <api>` is at http://127.0.0.1:8080/pub2tools/api. How to obtain the IDF files ``biotools.idf`` and ``biotools.stemmed.idf`` is described in the `setup section of EDAMmap <https://edammap.readthedocs.io/en/stable/manual.html#setup>`_. In contrast to the command line :ref:`usage <usage_manual>` of Pub2Tools, the server will not log to a single `log file <https://pubfetcher.readthedocs.io/en/stable/output.html#log-file>`_, but with ``-l`` or ``--log`` a directory can be defined where log files, that are rotated daily, will be stored. The log directory will also contain daily rotated access logs compatible with Apache's combined format.

A public instance of Pub2Tools-Server is accessible at https://elixir.ut.ee/pub2tools, with the :ref:`API <api>` at https://elixir.ut.ee/pub2tools/api.
