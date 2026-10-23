# Building Custom Docker Images for Kafbat UI

This guide explains how to compile Kafbat UI from source and build a custom Docker container image for local development or deployment to your own container registry.

---

## Prerequisites

- **JDK 25+**
- **Docker daemon** installed and running
- **Node.js & pnpm**: Managed automatically by the Gradle Node plugin during the build if not installed locally.

---

## 1. Build Application JAR with Bundled Frontend

Kafbat UI packages both the Spring Boot WebFlux backend and the compiled React frontend into a single executable fat JAR. Run the Gradle build with `-Pinclude-frontend=true` to compile the TypeSpec API contracts, build the React web application, and package all static assets into the JAR:

```bash
./gradlew clean build -Pinclude-frontend=true -x test
```

The resulting fat JAR will be located at:
```
api/build/libs/api-<version>.jar
```
*(e.g., `api/build/libs/api-0.0.1-SNAPSHOT.jar`)*

---

## 2. Build the Docker Image

You can build the Docker image using either the **Docker CLI** or **Gradle**:

### Option A: Using the Docker CLI (Recommended)

From the repository root, run:

```bash
# Locate the built JAR file
JAR_PATH=$(ls api/build/libs/api-*.jar | head -n 1)

# Build the Docker image using api/Dockerfile
docker build -t kafbat-ui:dev -f api/Dockerfile --build-arg JAR_FILE="${JAR_PATH}" api
```

### Option B: Using Gradle (Automated)

Gradle includes a task to build and register the Docker image directly with the local Docker daemon:

```bash
./gradlew clean build -Pinclude-frontend=true -Pbuild-docker-images=true -x test
```

This builds the complete JAR and produces a local Docker image tagged as:
```
ghcr.io/kafbat/kafka-ui:<version>
```

---

## 3. Run the Custom Docker Image

### Quick Run
```bash
docker run -it -p 8080:8080 \
  -e DYNAMIC_CONFIG_ENABLED=true \
  -e SWAGGER_UI_ENABLED=true \
  kafbat-ui:dev
```
Access the UI in your browser at `http://localhost:8080`.

### Run with a Custom Configuration File
To mount an external configuration file:

```bash
docker run -it -p 8080:8080 \
  -e DYNAMIC_CONFIG_ENABLED=true \
  -v $(pwd)/config.yml:/etc/kafkaui/dynamic_config.yaml \
  kafbat-ui:dev
```

---

## 4. Tag & Push to Your Development / Private Registry

To deploy the image to your development server or private container registry:

```bash
# Tag for your target registry
docker tag kafbat-ui:dev <your-registry-host>/kafbat-ui:<tag>

# Push to your registry
docker push <your-registry-host>/kafbat-ui:<tag>
```
