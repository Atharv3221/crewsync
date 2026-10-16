# Server image: build with the Maven wrapper on a JDK, run on a JRE as a non-root user.
#   docker build -t crewsync-server server/
#   docker run -p 8080:8080 -e JWT_SECRET_CREWSYNC=... -e DATABASE_URL_CREWSYNC=... crewsync-server

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/crewsync-*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 crewsync
COPY --from=build /app/app.jar app.jar
USER crewsync
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
