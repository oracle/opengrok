# Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.

FROM ubuntu:bionic as build

RUN apt-get update && apt-get install -y openjdk-8-jdk python3 maven python3-venv

# Create a first layer to cache the "Maven World" in the local repository.
# Incremental docker builds will always resume after that, unless you update the pom
WORKDIR /mvn
COPY pom.xml /mvn/
COPY opengrok-indexer/pom.xml /mvn/opengrok-indexer/
COPY opengrok-web/pom.xml /mvn/opengrok-web/
COPY plugins/pom.xml /mvn/plugins/
COPY suggester/pom.xml /mvn/suggester/

# distribution and opengrok-tools do not have dependencies to cache
RUN sed -i 's:<module>distribution</module>::g' /mvn/pom.xml
RUN sed -i 's:<module>opengrok-tools</module>::g' /mvn/pom.xml

RUN mkdir -p /mvn/opengrok-indexer/target/jflex-sources
RUN mkdir -p /mvn/opengrok-web/src/main/webapp
COPY opengrok-indexer/jflex-tt.txt /mvn/opengrok-indexer/jflex-tt.txt
COPY opengrok-indexer/jflex-tt-end.txt /mvn/opengrok-indexer/jflex-tt-end.txt
COPY opengrok-indexer/jflex-code.txt /mvn/opengrok-indexer/jflex-code.txt
COPY opengrok-indexer/jflex-code-end.txt /mvn/opengrok-indexer/jflex-code-end.txt
RUN mkdir -p /mvn/opengrok-web/src/main/webapp/WEB-INF/ && touch /mvn/opengrok-web/src/main/webapp/WEB-INF/web.xml

# dummy build to cache the dependencies
RUN mvn -DskipTests -Dcheckstyle.skip -Dmaven.antrun.skip package

# build the project
COPY ./ /opengrok-source
WORKDIR /opengrok-source

RUN mvn -DskipTests=true -Dmaven.javadoc.skip=true -B -V package
RUN cp distribution/target/*.tar.gz /opengrok.tar.gz

FROM tomcat:9-jre8
LABEL maintainer="opengrok-dev@yahoogroups.com"

# install dependencies and Python tools
RUN apt-get update && apt-get install -y git subversion mercurial unzip inotify-tools python3 python3-pip python3-venv

# compile and install universal-ctags
RUN apt-get install -y pkg-config autoconf build-essential && git clone https://github.com/universal-ctags/ctags /root/ctags && \
    cd /root/ctags && ./autogen.sh && ./configure && make && make install && \
    apt-get remove -y autoconf build-essential && apt-get -y autoremove && apt-get -y autoclean && \
    cd /root && rm -rf /root/ctags

# prepare OpenGrok binaries and directories
COPY --from=build opengrok.tar.gz /opengrok.tar.gz
RUN mkdir -p /opengrok /opengrok/etc /opengrok/data /opengrok/src && \
    tar -zxvf /opengrok.tar.gz -C /opengrok --strip-components 1 && \
    rm -f /opengrok.tar.gz

RUN python3 -m pip install /opengrok/tools/opengrok-tools*

# environment variables
ENV SRC_ROOT /opengrok/src
ENV DATA_ROOT /opengrok/data
ENV OPENGROK_WEBAPP_CONTEXT /
ENV OPENGROK_TOMCAT_BASE /usr/local/tomcat
ENV CATALINA_HOME /usr/local/tomcat
ENV CATALINA_BASE /usr/local/tomcat
ENV CATALINA_TMPDIR /usr/local/tomcat/temp
ENV PATH $CATALINA_HOME/bin:$PATH
ENV JRE_HOME /usr
ENV CLASSPATH /usr/local/tomcat/bin/bootstrap.jar:/usr/local/tomcat/bin/tomcat-juli.jar

# custom deployment to / with redirect from /source
RUN rm -rf /usr/local/tomcat/webapps/* && \
    opengrok-deploy -c /opengrok/etc/configuration.xml \
        /opengrok/lib/source.war /usr/local/tomcat/webapps/ROOT.war && \
    mkdir "/usr/local/tomcat/webapps/source" && \
    echo '<% response.sendRedirect("/"); %>' > "/usr/local/tomcat/webapps/source/index.jsp"

# disable all file logging
ADD docker/logging.properties /usr/local/tomcat/conf/logging.properties
RUN sed -i -e 's/Valve/Disabled/' /usr/local/tomcat/conf/server.xml

# add our scripts
ADD docker /scripts
RUN chmod -R +x /scripts

# run
WORKDIR $CATALINA_HOME
EXPOSE 8080
CMD ["/scripts/start.sh"]
