# Pub2Tools

Pub2Tools is a Java command-line tool that looks through the scientific literature available in [Europe PMC](https://europepmc.org/) and constructs entry candidates for the [bio.tools](https://bio.tools/) software registry from suitable publications. It automates a lot of the process needed for growing bio.tools, though results of the tool still need some manual curation before they are of satisfactory quality. Pub2Tools could be run at the beginning of each month to add hundreds of tools, databases and services published in bioinformatics and life sciences journals during the previous month.

## Overview

First, Pub2Tools gets a list of publications for the given period by narrowing down the entire selection with combinations of keyphrases. Next, the contents of these publications are downloaded and the abstract of each publication is mined for the potential tool name. Names are assigned confidence scores, with low confidence publications often not being suitable for bio.tools at all. In addition to the tool name, web links matching the name are extracted from the abstract and full text of a publication and divided to the homepage and other link attributes of bio.tools. In a second pass of the algorithm, the content of links and publications is also mined for software license and programming language information and phrases for the tool description attribute are automatically constructed. Terms from the [EDAM ontology](http://edamontology.org/page) are added to get the final results. Good enough non-existing results are chosen for inclusion to bio.tools. In addition to finding new content for bio.tools, Pub2Tools can also be used to improve the current content when run on existing entries of bio.tools.

## Dependencies

[PubFetcher](https://github.com/edamontology/pubfetcher) is used for downloading publications and links and [EDAMmap](https://github.com/edamontology/edammap) is used for adding EDAM ontology annotations to the new bio.tools entries.

## Install

Installation instructions can be found in [INSTALL.md](INSTALL.md).

## Documentation

Documentation for Pub2Tools can be found at https://pub2tools.readthedocs.io/.
