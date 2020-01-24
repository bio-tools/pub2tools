# INSTALL

## Compiling from latest source

[git](https://git-scm.com/), [JDK 8](https://openjdk.java.net/projects/jdk8/) (or later) and [Apache Maven](https://maven.apache.org/) are required.

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
