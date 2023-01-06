FROM docker-proxy.devops.projectronin.io/alpine:3.16

RUN adduser -S -D ronin
RUN apk add --no-cache bash openjdk17 gradle git && rm -rf /var/cache/apk/*

ADD ./config /etc/contract-tools-config
RUN mkdir /home/ronin/.m2 && mkdir /home/ronin/.m2/repository && chown -R ronin /home/ronin/.m2
USER ronin
WORKDIR /app

CMD /usr/bin/gradle
