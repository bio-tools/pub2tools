# INSTALL

## Compiling from latest source

[git](https://git-scm.com/), [JDK 11](https://openjdk.org/) (or later) and [Apache Maven](https://maven.apache.org/) are required.

In addition, [installation instructions for PubFetcher](https://github.com/edamontology/pubfetcher/blob/master/INSTALL.md) and [installation instructions for EDAMmap](https://github.com/edamontology/edammap/blob/master/INSTALL.md) have to be followed beforehand to ensure PubFetcher and EDAMmap dependencies are installed in the local Maven repository.

Execute:

```shell
$ cd ~/foo/bar/
$ git clone https://github.com/bio-tools/pub2tools.git
$ cd pub2tools/
$ git checkout develop
$ mvn clean install
```

Pub2Tools can now be run with:

```shell
$ java -jar ~/foo/bar/pub2tools/target/pub2tools-<version>.jar -h
```

A packaged version of Pub2Tools can be found as `~/foo/bar/pub2tools/target/pub2tools-<version>.zip`.

## Compiling latest release

Same as previous section, except `git checkout develop` must be replaced with `git checkout master`.

## Using a pre-compiled release

Pre-built releases can be found from https://github.com/bio-tools/pub2tools/releases. A downloaded release package can be unzipped in the desired location, where `pub2tools-<version>.jar` can again be run with `java -jar`.

## Dependencies

By default, [Selenium WebDriver](https://www.selenium.dev/documentation/webdriver/) is used to get the content of web pages that require JavaScript. Currently, only the Firefox driver is supported, meaning that Firefox needs to be installed (or the firefox executable available somewhere and pointed to with ``--seleniumFirefox /path/to/firefox``), otherwise Pub2Tools will fail to start. If this not desirable, then usage of Selenium can be disabled with ``--selenium false``, in which case the included [HtmlUnit](https://htmlunit.sourceforge.io/) library will be used instead (however, this will be slower and with worse quality results).
