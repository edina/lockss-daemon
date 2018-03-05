FROM nimmis/java-centos:openjdk-7-jdk

ENV ANT_VERSION 1.9.10
ENV ANT_HOME /usr/local/ant
RUN wget "http://apache.mirror.anlx.net/ant/binaries/apache-ant-$ANT_VERSION-bin.tar.gz" && \
    tar -xzf "apache-ant-$ANT_VERSION-bin.tar.gz" && \
    mv "apache-ant-$ANT_VERSION" "$ANT_HOME" && \
    rm "apache-ant-$ANT_VERSION-bin.tar.gz"
ENV PATH $PATH:$ANT_HOME/bin
ENV JAVA_HOME /usr/lib/jvm/java/
ENV RPM_RELEASE 1.keepsafe.el7
ENV RPM_RELEASE_NAME 1.73.2

# Needed for xmllint, required by LOCKSS build.xml
#RUN yum install --assumeyes libxml2-utils
RUN yum install --assumeyes git
RUN yum install --assumeyes rpm-build

COPY ./ /usr/src/lockss-daemon
WORKDIR /usr/src/lockss-daemon

CMD ant -Drpmrelease="$RPM_RELEASE" -Dreleasename="$RPM_RELEASE_NAME" rpm
