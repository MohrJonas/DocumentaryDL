FROM bellsoft/liberica-openjdk-alpine:11

MAINTAINER Jonas Mohr

RUN apk add --no-cache git maven ffmpeg python3 py3-pip gcc musl-dev
RUN python3 -m pip install -U yt-dlp

WORKDIR /
RUN git clone https://github.com/NastyGamer/DocumentaryDL

WORKDIR /DocumentaryDL
RUN mvn package

ENTRYPOINT ["java", "-server", "-jar", "target/documentary-dl-1.0-SNAPSHOT.jar"]