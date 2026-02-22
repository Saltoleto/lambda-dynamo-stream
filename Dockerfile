# Optional: build the shaded jar without installing Maven locally
# docker build -t dynamodb-stream-lambda-build .
# docker run --rm -v "$PWD/target:/workspace/target" dynamodb-stream-lambda-build

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

# Export only the build artifact
FROM alpine:3.20
WORKDIR /workspace
COPY --from=build /workspace/target/dynamodb-stream-lambda-1.0.0-shaded.jar /workspace/target/dynamodb-stream-lambda-1.0.0-shaded.jar
CMD ["sh", "-lc", "ls -lah /workspace/target && echo 'Jar ready.'"]
