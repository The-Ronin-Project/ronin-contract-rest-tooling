FROM docker-proxy.devops.projectronin.io/alpine:3.16

RUN adduser -S -D ronin
RUN apk add --no-cache bash npm openjdk17 && rm -rf /var/cache/apk/*
RUN npm install -g @stoplight/spectral-cli@~6.6.0 @openapitools/openapi-generator-cli

ADD --chown=ronin ./contract-tools /usr/local/bin
ADD ./config /etc/contract-tools-config
RUN chmod 755 /usr/local/bin/contract-tools
RUN mkdir /usr/local/lib/node_modules/@openapitools/openapi-generator-cli/versions \
    && chown ronin /usr/local/lib/node_modules/@openapitools/openapi-generator-cli/versions
USER ronin
WORKDIR /app

CMD /usr/local/bin/contract-tools
