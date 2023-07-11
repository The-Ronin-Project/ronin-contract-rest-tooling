FROM gradle:8.2.0-jdk17

RUN useradd -rm ronin
RUN curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && apt-get install -y nodejs && apt-get clean && npm install -g @stoplight/spectral-cli@~6.6.0

ADD ./config /etc/contract-tools-config

USER ronin

WORKDIR /app

CMD /usr/bin/gradle
