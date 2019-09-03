# INSTALL

[Apache Maven](https://maven.apache.org/) is required.

Also, [PubFetcher](https://github.com/edamontology/pubfetcher) must be [installed](https://github.com/edamontology/pubfetcher/blob/master/INSTALL.md) and [EDAMmap](https://github.com/edamontology/edammap) must be [installed](https://github.com/edamontology/edammap/blob/master/INSTALL.md) in local Maven repository.

```shell
$ git clone https://github.com/bio-tools/pub2tools.git
$ cd pub2tools/
$ mvn clean install
$ java -jar target/pub2tools-0.2-SNAPSHOT.jar -h
```
