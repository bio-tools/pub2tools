
##################
What is Pub2Tools?
##################

Pub2Tools is a Java command-line tool that looks through the scientific literature available in `Europe PMC <https://europepmc.org/>`_ and constructs entry candidates for the `bio.tools <https://bio.tools/>`_ software registry from suitable publications. It automatises a lot of the process of growing bio.tools, though results of the tool still need some manual curation before they can be incorporated into bio.tools. Pub2Tools could be run at the beginning of each month to add tools, databases and services published in bioinformatics and life sciences journals during the previous month.


********
Overview
********

Pub2Tools can be run from start to finish with only one command (:ref:`all`). But :ref:`setup commands <setup_commands>` (fetching or copying the required input files) and :ref:`steps <steps>` (fetching of publications and web pages, mapping to `EDAM ontology <http://edamontology.org/page>`_ terms and applying the Pub2Tools algorithm) can also be applied individually. Execution can be resumed by restarting the last aborted step (:ref:`resume`). Commands can be influenced by changing the default values of :ref:`parameters <parameters>`. Some :ref:`examples <examples>` of running Pub2Tools are provided. One interesting example is :ref:`improving existing bio.tools entries <improving_existing>` added through some other means than Pub2Tools.

All files of one Pub2Tools run will end up in an :ref:`output directory <output_directory>` chosen by the user. All prerequisite and intermediate files will be saved for reproducibility and debugging purposed. The main results files are :ref:`results_csv` (contains all possible results), :ref:`diff_csv` (contains fix suggestions to existing bio.tools content) and :ref:`to_biotools_json` (contains new entries to be imported into bio.tools). The following bio.tools attributes can be filled by Pub2Tools: `name <https://biotools.readthedocs.io/en/latest/curators_guide.html#name-tool>`_, `description <https://biotools.readthedocs.io/en/latest/curators_guide.html#description>`_, `homepage <https://biotools.readthedocs.io/en/latest/curators_guide.html#homepage>`_, `function <https://biotools.readthedocs.io/en/latest/curators_guide.html#function-group>`_, `topic <https://biotools.readthedocs.io/en/latest/curators_guide.html#topic>`_, `language <https://biotools.readthedocs.io/en/latest/curators_guide.html#programming-language>`_, `license <https://biotools.readthedocs.io/en/latest/curators_guide.html#license>`_, `link <https://biotools.readthedocs.io/en/latest/curators_guide.html#link-group>`_, `download <https://biotools.readthedocs.io/en/latest/curators_guide.html#download-group>`_, `documentation <https://biotools.readthedocs.io/en/latest/curators_guide.html#documentation-group>`_, `publication  <https://biotools.readthedocs.io/en/latest/curators_guide.html#publication-group>`_, `credit <https://biotools.readthedocs.io/en/latest/curators_guide.html#credit-group>`_. But not all attributes can always be filled, as shown in :ref:`performance`, and sometimes they are filled incorrectly, so Pub2Tools results imported into bio.tools still need some fixing and manual curation. Per month, roughly 500 entries could potentially be added to bio.tools from Pub2Tools results.


************
Dependencies
************

For selecting suitable publications and downloading their content, Pub2Tools is leveraging `Europe PMC`_, which among other things allows the `inclusion of preprints <http://blog.europepmc.org/2018/07/preprints.html>`_.

Publications are downloaded through the `PubFetcher <https://github.com/edamontology/pubfetcher>`_ library, that in addition to Europe PMC supports fetching publication content from other resources as fallback, for example directly from publisher web sites using the given DOI. In addition, PubFetcher provides support for downloading the content of links extracted by Pub2Tools and provides a database for storing all downloaded content.

Pub2Tools is also leveraging `EDAMmap <https://github.com/edamontology/edammap>`_, for preprocessing of input free text (including the extraction of links), for downloading and loading of bio.tools content, for `tf–idf <https://en.wikipedia.org/wiki/Tf%E2%80%93idf>`_ support, and of course, for mapping of entries to `EDAM ontology`_ terms.


*******
Caveats
*******

Inevitably, there will be false positives and false negatives, both at entry level (some suggested tools are not actual tools and some actual tools are missed by Pub2Tools) and at individual attribute level. Generally, if we try to decrease the number of FN entries, the number of FPs also tends to increase. Currently, Pub2Tools has been tuned to not have too many FPs, to not discourage curators into looking at all entries in the results. Some FNs are rather hopeless: quite obviously, unpublished tools can't be found by Pub2Tools, but in addition, there is the limitation that the tool name must be mentioned somewhere in the publication title or abstract.

For slightly better results, before a bigger run of Pub2Tools, it could be beneficial to `test if PubFetcher scraping rules <https://github.com/edamontology/pubfetcher/wiki/scraping#testing-of-rules>`_ are still up to date. Also, publisher web sites have to be consulted sometimes, so it could be beneficial to run Pub2Tools in a network with good access to journal articles.

Pub2Tools assigns a score for each result entry and orders the results based on this score. However, this score does not describe how "good" or high impact the tool itself is, but rather how confidently the tool name was extracted. A higher score is obtained if the name of the tool is unique, put to the start of the publication title, surrounded by certain keywords (like "called" or "freely") in the abstract and matches a URL in the abstract (but also in the publication full text).


*******
Install
*******

Installation instructions can be found in the project's GitHub repo at `INSTALL <https://github.com/bio-tools/pub2tools/blob/master/INSTALL.md>`_.


**********
Quickstart
**********

This will generate results to the directory ``output`` for publications added to Europe PMC on the 23rd of August 2019:

.. code-block:: bash

  $ java -jar path/to/pub2tools-<version>.jar -all output \
  --edam http://edamontology.org/EDAM.owl \
  --idf https://github.com/edamontology/edammap/raw/master/doc/biotools.idf \
  --idf-stemmed https://github.com/edamontology/edammap/raw/master/doc/biotools.stemmed.idf \
  --day 2019-08-23

If this quick example worked, then for the next incarnations of Pub2Tools, the ``EDAM.owl`` and ``.idf`` files could be downloaded to local disk and the corresponding local paths used in the command instead of the URLs, and ``--month 2019-08`` could be used instead of ``--day 2019-08-23`` to fetch results for an entire month. Explanations for the columns and attributes of the results files can be found in the documentation at :ref:`results_csv_columns`, :ref:`diff_csv_columns` and :ref:`to_biotools_attributes`.


****
Repo
****

Pub2Tools is hosted at https://github.com/bio-tools/pub2tools.


*******
Support
*******

Should you need help installing or using Pub2Tools, please get in touch with Erik Jaaniso (the lead developer) directly via the `tracker <https://github.com/bio-tools/pub2tools/issues>`_.


*******
License
*******

Pub2Tools is free and open-source software licensed under the GNU General Public License v3.0, as seen in `COPYING <https://github.com/bio-tools/pub2tools/blob/master/COPYING>`_.
