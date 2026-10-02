FROM clojure:temurin-21-tools-deps
WORKDIR /app
COPY deps.edn ./
RUN clojure -P
COPY src src
EXPOSE 8088
CMD ["clojure", "-M", "-m", "befive.mock-idp.main"]
