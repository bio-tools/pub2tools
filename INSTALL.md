# INSTALL

[git](https://git-scm.com/), [JDK 8](https://openjdk.java.net/projects/jdk8/) (or later) and [Apache Maven](https://maven.apache.org/) are required.

In addition, [installation instructions for PubFetcher](https://github.com/edamontology/pubfetcher/blob/master/INSTALL.md) and [installation instructions for EDAMmap](https://github.com/edamontology/edammap/blob/master/INSTALL.md) have to be followed beforehand to ensure PubFetcher and EDAMmap dependencies are installed in the local Maven repository.

On the command-line, go to the directory Pub2Tools should be installed in and execute:

```shell
$ git clone https://github.com/bio-tools/pub2tools.git
$ cd pub2tools/
$ mvn clean install
```

Pub2Tools can now be run with:

```shell
$ java -jar /path/to/pub2tools/target/pub2tools-0.2-SNAPSHOT.jar -h
```
