FROM eclipse-temurin:17-jdk-jammy

ARG ANDROID_COMMANDLINE_TOOLS_VERSION=14742923
ARG ANDROID_BUILD_TOOLS_VERSION=36.0.0
ARG ANDROID_PLATFORM_VERSION=android-36
ARG GRADLE_VERSION=9.3.1
ARG GRADLE_SHA256=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06
ARG UID=1000
ARG GID=1000

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV GRADLE_HOME=/opt/gradle/gradle-${GRADLE_VERSION}
ENV GRADLE_USER_HOME=/home/android/.gradle
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${GRADLE_HOME}/bin:${PATH}"

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        git \
        unzip \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" /opt/gradle \
    && curl -fsSL \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_COMMANDLINE_TOOLS_VERSION}_latest.zip" \
        -o /tmp/android-commandline-tools.zip \
    && unzip -q /tmp/android-commandline-tools.zip -d /tmp/android-commandline-tools \
    && mv /tmp/android-commandline-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/android-commandline-tools /tmp/android-commandline-tools.zip

RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
        -o /tmp/gradle.zip \
    && echo "${GRADLE_SHA256}  /tmp/gradle.zip" | sha256sum -c - \
    && unzip -q /tmp/gradle.zip -d /opt/gradle \
    && rm /tmp/gradle.zip

RUN yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses >/dev/null || true \
    && sdkmanager --sdk_root="${ANDROID_HOME}" \
        "platform-tools" \
        "platforms;${ANDROID_PLATFORM_VERSION}" \
        "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" \
    && (yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses >/dev/null || true)

RUN groupadd --gid "${GID}" android \
    && useradd --uid "${UID}" --gid "${GID}" --create-home --shell /bin/bash android \
    && mkdir -p /workspace "${GRADLE_USER_HOME}" \
    && chown -R android:android /workspace /home/android

WORKDIR /workspace
USER android

ENTRYPOINT ["gradle"]
