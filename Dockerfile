# syntax=docker/dockerfile:1

# ---- Stage 1: build the production jar (incl. the Vaadin frontend bundle) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# The BuildKit cache mount keeps the local Maven repo across builds, so only the first
# build pays the full dependency + Vaadin frontend download cost.
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Pproduction clean package -DskipTests

# ---- Stage 2: slim runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# curl is only needed for the container HEALTHCHECK below.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged account.
RUN groupadd --system prioritize \
    && useradd --system --gid prioritize --home-dir /app/data prioritize

# The production build emits exactly one bootable jar; the *.original artifact does not
# end in .jar, so the wildcard picks only the runnable one regardless of the version.
COPY --from=build /build/target/prioritize-*.jar app.jar

# /app/data holds the H2 file DB (default profile) and the Tomcat work/log dirs; declare
# it a volume so a standalone `docker run` keeps its data across restarts.
RUN mkdir -p /app/data \
    && chown -R prioritize:prioritize /app

# The H2 URL uses ~ (home) for its file DB, and the JVM takes user.home from the passwd
# entry - not from $HOME - so the account above is created with /app/data as its home.
ENV HOME=/app/data
VOLUME ["/app/data"]

EXPOSE 8080

# /login is public (Vaadin login view) and returns 200 once the app is fully up.
# start-period covers the frontend/engine warm-up so early probes don't mark it unhealthy.
HEALTHCHECK --start-period=120s --interval=15s --timeout=5s --retries=5 \
    CMD curl --fail --silent --show-error http://localhost:8080/login || exit 1

# The entrypoint runs as root only long enough to make the data volume writable for the
# service account, then drops to it. See docker/entrypoint.sh.
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]
