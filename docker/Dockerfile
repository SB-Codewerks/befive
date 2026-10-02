# BeFive runtime. JDK 21, jlink, distroless, UID 10001.
# The jar name matches build.clj's version.

FROM eclipse-temurin:21-jdk-jammy AS build
ARG CLOJURE_VERSION=1.12.6.1673
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && curl -fsSL -o /tmp/linux-install.sh \
      "https://github.com/clojure/brew-install/releases/download/${CLOJURE_VERSION}/linux-install.sh" \
 && bash /tmp/linux-install.sh \
 && rm -rf /var/lib/apt/lists/* /tmp/linux-install.sh
WORKDIR /src
COPY deps.edn ./
RUN clojure -P
COPY build.clj ./
COPY modules modules
RUN clojure -T:build uber

FROM eclipse-temurin:21-jdk-jammy AS jre
RUN jlink \
      --add-modules java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.management,jdk.unsupported,jdk.zipfs \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress=2 \
      --output /javaruntime \
 && mkdir -p /var/lib/befive/lkg \
 && chown -R 10001:10001 /var/lib/befive

FROM busybox:1.36.1-musl AS busybox

FROM gcr.io/distroless/base-debian12
COPY --from=jre /javaruntime /opt/java
COPY --from=jre --chown=10001:10001 /var/lib/befive /var/lib/befive
COPY --from=build --chown=10001:10001 \
     /src/target/befive-0.1.0-SNAPSHOT.jar /opt/befive/befive.jar
COPY --from=busybox /bin/busybox /busybox
COPY docker/entrypoint.sh /entrypoint.sh
ENV HOME=/tmp \
    LANG=C.UTF-8 \
    PATH=/opt/java/bin:${PATH}
USER 10001:10001
WORKDIR /opt/befive
EXPOSE 8080 9000 9901
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD ["/opt/java/bin/java", "-jar", "/opt/befive/befive.jar", \
       "healthcheck", "/readyz"]
ENTRYPOINT ["/busybox", "sh", "/entrypoint.sh"]
