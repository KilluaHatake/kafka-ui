# Building Custom Docker Images for Kafbat UI

This guide explains how to compile Kafbat UI from source and build custom Docker container images across different host operating systems (macOS, Linux, Windows) and target CPU architectures (`linux/amd64`, `linux/arm64`, or multi-arch).

---

## Prerequisites

- **JDK 25+** (see [`.java-version`](.java-version))
- **Docker** installed and running (Docker Desktop, Docker Engine, Colima, OrbStack, or Rancher Desktop)
- **Node.js (v22+) & pnpm (v10+)**: By default, `local_node = true` in [`gradle.properties`](gradle.properties) uses your locally installed `node` and `pnpm`. If they are not installed locally, pass `-Plocal_node=false` to let Gradle download them automatically.

### Configure `JAVA_HOME` by Host OS

Ensure `JAVA_HOME` points to JDK 25 before running Gradle (especially if another JDK such as 11 or 17 is first on your `PATH`):

- **macOS (Apple Silicon, Homebrew):**
  ```bash
  export JAVA_HOME="/opt/homebrew/opt/openjdk@25"
  ```
- **macOS (Intel, Homebrew):**
  ```bash
  export JAVA_HOME="/usr/local/opt/openjdk@25"
  ```
- **macOS (Framework / Oracle / Temurin JDK):**
  ```bash
  export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
  ```
- **Linux (apt / tarball / SDKMAN):**
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-25-openjdk-amd64"
  # Or with SDKMAN: sdk use java 25-tem
  ```
- **Windows (PowerShell):**
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25"
  ```
  *(If using WSL2 or Git Bash on Windows, follow the Linux commands above.)*

---

## 1. Build Application JAR with Bundled Frontend

Kafbat UI packages both the Spring Boot WebFlux backend and the compiled React frontend into a single executable fat JAR. Run Gradle with `-Pinclude-frontend=true` to compile the TypeSpec API contracts, build the React web application, and package all static assets into the JAR.

### macOS / Linux / Git Bash / WSL2

```bash
./gradlew clean build -Pinclude-frontend=true -x test
```

### Windows (PowerShell)

```powershell
.\gradlew.bat clean build -Pinclude-frontend=true -x test
```

*(Append `-Plocal_node=false` if `node` and `pnpm` are not installed locally.)*

Gradle produces two JARs in `api/build/libs/`:
- `api/build/libs/api-<version>.jar` — the executable Spring Boot fat JAR (e.g., `api/build/libs/api-0.0.1-SNAPSHOT.jar`)
- `api/build/libs/api-<version>-plain.jar` — non-executable library JAR (**do not** pass this to Docker)

---

## 2. Build the Docker Image

### Important Notes on Build Context & `JAR_FILE`

When building with [`api/Dockerfile`](api/Dockerfile):
- The Docker build context is **`api`** (the last argument), **not** the repository root `.`.
- Therefore, `--build-arg JAR_FILE=...` **must be relative to `api/`** (for example `build/libs/api-0.0.1-SNAPSHOT.jar`), **not** `api/build/libs/api-0.0.1-SNAPSHOT.jar`.
- Passing `api/build/libs/...` with context `api` causes Docker to look for `api/api/build/libs/...` and fail with `COPY failed: file not found in build context`.
- Also avoid `ls api/build/libs/api-*.jar | head -n 1`, because `-plain.jar` sorts alphabetically before `.jar` (`-` precedes `.` in ASCII).

[`api/Dockerfile`](api/Dockerfile) uses `azul/zulu-openjdk-alpine`, which publishes a multi-architecture manifest supporting both **`linux/amd64`** and **`linux/arm64`**.

---

### Option A: Using the Docker CLI (Recommended)

#### 1. Native Host Platform (Default)

Builds for the architecture of your local Docker engine (`linux/arm64` on Apple Silicon / ARM64 servers, `linux/amd64` on Intel / x86_64 machines).

**macOS / Linux:**
```bash
JAR_FILE=$(cd api && ls build/libs/api-*.jar | grep -v -- '-plain\.jar$' | head -n 1)

docker build \
  -t kafbat-ui:dev \
  -f api/Dockerfile \
  --build-arg JAR_FILE="${JAR_FILE}" \
  api
```

**Windows (PowerShell):**
```powershell
$JAR_FILE = "build/libs/" + (Get-ChildItem api/build/libs/api-*.jar -Exclude *-plain.jar | Select-Object -First 1 -ExpandProperty Name)

docker build `
  -t kafbat-ui:dev `
  -f api/Dockerfile `
  --build-arg JAR_FILE="$JAR_FILE" `
  api
```

#### 2. Cross-Platform Build for `linux/amd64` (e.g., from Apple Silicon Mac to x86_64 Servers)

**macOS / Linux:**
```bash
JAR_FILE=$(cd api && ls build/libs/api-*.jar | grep -v -- '-plain\.jar$' | head -n 1)

docker build --platform linux/amd64 \
  -t kafbat-ui:dev \
  -f api/Dockerfile \
  --build-arg JAR_FILE="${JAR_FILE}" \
  api
```

**Windows (PowerShell):**
```powershell
docker build --platform linux/amd64 `
  -t kafbat-ui:dev `
  -f api/Dockerfile `
  --build-arg JAR_FILE=build/libs/api-0.0.1-SNAPSHOT.jar `
  api
```

#### 3. Cross-Platform Build for `linux/arm64` (e.g., from x86_64 Linux/Windows to AWS Graviton / ARM64)

```bash
docker build --platform linux/arm64 \
  -t kafbat-ui:dev \
  -f api/Dockerfile \
  --build-arg JAR_FILE=build/libs/api-0.0.1-SNAPSHOT.jar \
  api
```

> [!NOTE]
> Cross-architecture builds require QEMU/binfmt emulation (enabled by default in Docker Desktop, OrbStack, and Colima; on Linux hosts, you may need `docker run --privileged --rm tonistiigi/binfmt --install all`).

#### 4. Multi-Architecture Build & Push (`linux/amd64` + `linux/arm64`) with Buildx

To build and publish a single multi-arch image manifest to a container registry (requires the `docker buildx` plugin):

```bash
docker buildx create --name multiarch --use 2>/dev/null || docker buildx use multiarch

docker buildx build --platform linux/amd64,linux/arm64 \
  -t <your-registry-host>/kafbat-ui:<tag> \
  -f api/Dockerfile \
  --build-arg JAR_FILE=build/libs/api-0.0.1-SNAPSHOT.jar \
  --push \
  api
```

*(Note: Classic Docker image stores cannot `--load` a multi-architecture manifest list locally; use `--push` to a registry, or build single `--platform` targets locally.)*

---

### Option B: Using Gradle (Automated Native Build)

Gradle includes `buildDockerImageTask` in [`api/build.gradle`](api/build.gradle) to build and register the Docker image directly with the local Docker daemon (using the daemon's native platform):

```bash
./gradlew clean build -Pinclude-frontend=true -Pbuild-docker-images=true -x test
```

This builds the complete JAR and produces a local Docker image tagged as:
```
ghcr.io/kafbat/kafka-ui:<version>
```
*(e.g., `ghcr.io/kafbat/kafka-ui:0.0.1-SNAPSHOT`)*

### Option C: Alternative Local Dockerfile (`Dockerfile.local`)

If you prefer a Debian-based `eclipse-temurin:25` image instead of Alpine/Zulu, [`Dockerfile.local`](Dockerfile.local) expects the **repository root `.`** as the build context:

```bash
docker build -t kafbat-ui:dev -f Dockerfile.local --build-arg JAR_FILE=api/build/libs/api-0.0.1-SNAPSHOT.jar .
```

---

## 3. Verify & Run the Custom Docker Image

Verify the built image architecture:
```bash
docker inspect --format '{{.Os}}/{{.Architecture}} {{json .RepoTags}}' kafbat-ui:dev
```

### Quick Run
```bash
docker run -it -p 8080:8080 \
  -e DYNAMIC_CONFIG_ENABLED=true \
  -e SWAGGER_UI_ENABLED=true \
  kafbat-ui:dev
```
Access the UI in your browser at `http://localhost:8080`.

### Run with a Custom Configuration File

**macOS / Linux:**
```bash
docker run -it -p 8080:8080 \
  -e DYNAMIC_CONFIG_ENABLED=true \
  -v "$(pwd)/config.yml:/etc/kafkaui/dynamic_config.yaml" \
  kafbat-ui:dev
```

**Windows (PowerShell):**
```powershell
docker run -it -p 8080:8080 `
  -e DYNAMIC_CONFIG_ENABLED=true `
  -v "${PWD}/config.yml:/etc/kafkaui/dynamic_config.yaml" `
  kafbat-ui:dev
```

---

## 4. Tag & Push or Export Offline Tarball

### Push to a Container Registry

```bash
# Tag for your target registry
docker tag kafbat-ui:dev <your-registry-host>/kafbat-ui:<tag>

# Push to your registry
docker push <your-registry-host>/kafbat-ui:<tag>
```

### Export & Load for Offline / Air-Gapped Servers

If transferring the built image directly to another host (for example, an `amd64` server built from an Apple Silicon Mac):

```bash
# Save compressed image archive
docker save kafbat-ui:dev | gzip > kafbat-ui-dev.tgz

# Load on the target machine
docker load -i kafbat-ui-dev.tgz
```
