FROM maven:3.9.11-eclipse-temurin-21 AS java_tools
FROM sandcastle:yuweiju-afk-issue-4
USER root
COPY --from=java_tools /opt/java/openjdk /opt/java/openjdk
COPY --from=java_tools /usr/share/maven /usr/share/maven
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:/usr/share/maven/bin:${PATH}
RUN apt-get update && apt-get install -y --no-install-recommends python3 ripgrep chromium fonts-noto-cjk && rm -rf /var/lib/apt/lists/*
RUN npm install -g playwright@1.63.0 miniprogram-automator@0.12.1
USER 1000:1000
WORKDIR /home/agent
ENTRYPOINT ["sleep", "infinity"]
