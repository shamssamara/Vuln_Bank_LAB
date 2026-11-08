# Multi-stage Kotlin build; installDist avoids needing Gradle in the runtime container.
FROM gradle:8.14-jdk17 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /srv
COPY --from=build /home/gradle/project/server/build/install/server/ /srv/
COPY server/certs/server.p12 /srv/server.p12
ENV CERT_PATH=/srv/server.p12 CERT_PASSWORD=lab-changeit DB_URL=jdbc:postgresql://db:5432/vulnbank DB_USER=vulnbank DB_PASSWORD=vulnbank_lab
EXPOSE 8443
ENTRYPOINT ["/srv/bin/server"]
