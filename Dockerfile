FROM gradle:7.6.0-jdk17

RUN useradd -rm ronin
RUN curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && apt-get install -y nodejs && apt-get clean && npm install -g @stoplight/spectral-cli@~6.6.0

ADD ./config /etc/contract-tools-config
RUN mkdir /home/ronin/.m2 && mkdir /home/ronin/.m2/repository && chown -R ronin /home/ronin/.m2

RUN mkdir /home/ronin/gradle-temp

ADD ./plugin/build/libs/*.jar /home/ronin/gradle-temp
ADD ./plugin/build/publications/pluginMaven/* /home/ronin/gradle-temp
ADD ./plugin/build/publications/restContractSupportPluginMarkerMaven/pom-default.xml /home/ronin/gradle-temp/plugin-pom.xml
COPY ./docker-setup /home/ronin/gradle-temp

WORKDIR /home/ronin/gradle-temp

RUN chown -R ronin /home/ronin/gradle-temp

USER ronin

RUN export PLUGIN_VERSION=$(ls *.jar | perl -p -e 's/^.*plugin-(.*)\.jar$/$1/') && \
    mkdir -p /home/ronin/.m2/repository/com/projectronin/rest/contract/plugin/${PLUGIN_VERSION} && \
    mv plugin-${PLUGIN_VERSION}.jar /home/ronin/.m2/repository/com/projectronin/rest/contract/plugin/${PLUGIN_VERSION}/plugin-${PLUGIN_VERSION}.jar && \
    mv module.json /home/ronin/.m2/repository/com/projectronin/rest/contract/plugin/${PLUGIN_VERSION}/plugin-${PLUGIN_VERSION}.module && \
    mv pom-default.xml /home/ronin/.m2/repository/com/projectronin/rest/contract/plugin/${PLUGIN_VERSION}/plugin-${PLUGIN_VERSION}.pom && \
    mkdir -p /home/ronin/.m2/repository/com/projectronin/rest/contract/support/com.projectronin.rest.contract.support.gradle.plugin/${PLUGIN_VERSION} && \
    mv plugin-pom.xml /home/ronin/.m2/repository/com/projectronin/rest/contract/support/com.projectronin.rest.contract.support.gradle.plugin/${PLUGIN_VERSION}/com.projectronin.rest.contract.support.gradle.plugin-${PLUGIN_VERSION}.pom && \
    perl -pi -e "s/<VERSION>/${PLUGIN_VERSION}/g" build.gradle.kts && \
    ls /home/ronin/gradle-temp && \
    gradle build --stacktrace

RUN rm -rf /home/ronin/gradle-temp

WORKDIR /app

CMD /usr/bin/gradle
