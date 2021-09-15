# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

ARG base_image=kilda/base-ubuntu
FROM ${base_image}

ENV PACKAGE apache-storm-2.2.0

# must not be fetched during container build (i.e. must be prefetched and hardcoded during version bumps)
# can be fetched by bin/fetch-storm-sha512.sh
ARG PACKAGE_SHA512=2A25468D5FCA2BC6C63E7378300B961021AC321C91422D35455F18E61DF967B2CE675996508B5C4C14B5B884219C65ED76ABFB3E1359C4968C866EB226CAAAA5

WORKDIR /tmp/

RUN    \
    apt -y update \
    && apt -y install net-tools jq \
    && wget -q https://archive.apache.org/dist/storm/$PACKAGE/$PACKAGE.tar.gz \
    && echo "$PACKAGE_SHA512 $PACKAGE.tar.gz" > $PACKAGE.tar.gz.sha512 \
    && sha512sum -c $PACKAGE.tar.gz.sha512 \
    && tar -xzf $PACKAGE.tar.gz --directory /opt/ \
    && ln -s /opt/$PACKAGE /opt/storm \
    && ln -s /usr/bin/python3 /usr/bin/python \
    && rm -rfv /tmp/*

WORKDIR /opt/storm/

COPY storm.yaml /opt/storm/conf/storm.yaml
COPY log4j2 /opt/storm/log4j2
#COPY lib/*.jar /opt/storm/lib/
COPY simple-storm-supervisor-healthcheck.sh /simple-storm-supervisor-healthcheck.sh
COPY simple-storm-ui-healthcheck.sh /simple-storm-ui-healthcheck.sh
