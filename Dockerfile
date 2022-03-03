FROM bellsoft/liberica-openjdk-alpine:11

RUN apk add --no-cache git maven ffmpeg python3 py3-pip curl
RUN curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp
RUN chmod a+rx /usr/local/bin/yt-dlp

WORKDIR /
RUN git clone https://github.com/NastyGamer/DocumentaryDL

WORKDIR /DocumentaryDL
RUN mvn package

ENTRYPOINT ["java", "-server", "-jar", "target/consoleApp-1.0-SNAPSHOT.jar"]