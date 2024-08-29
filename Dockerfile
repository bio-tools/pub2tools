FROM debian:bookworm AS build
RUN apt-get update && apt-get install -y default-jdk-headless maven git
WORKDIR /opt/pub2tools
COPY . .
RUN git clone https://github.com/edamontology/pubfetcher.git && cd pubfetcher/ && git checkout develop && mvn clean install
RUN git clone https://github.com/edamontology/edammap.git && cd edammap/ && git checkout develop && mvn clean install
RUN mvn clean install

FROM debian:bookworm
RUN apt-get update && apt-get install -y default-jre firefox-esr
COPY --from=build /opt/pub2tools/target /opt/pub2tools
COPY --from=build /opt/pub2tools/edammap/doc/EDAM_1.25.owl /opt/pub2tools/edammap/doc/biotools.idf /opt/pub2tools/edammap/doc/biotools.stemmed.idf /opt/pub2tools
WORKDIR /var/lib/pub2tools
EXPOSE 8080/tcp
CMD ["java", "-jar", "/opt/pub2tools/pub2tools-server-1.1.2-SNAPSHOT.jar", "-b", "http://0.0.0.0:8080", "--httpsProxy", "-e", "/opt/pub2tools/EDAM_1.25.owl", "-f", "files", "--db", "server.db", "--idf", "/opt/pub2tools/biotools.idf", "--idfStemmed", "/opt/pub2tools/biotools.stemmed.idf", "--biotools", "biotools.json", "--log", "/var/log/pub2tools"]
