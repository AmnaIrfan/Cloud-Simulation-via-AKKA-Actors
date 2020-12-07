FROM openjdk:8

RUN \
  curl -L -o sbt-1.3.4.deb http://dl.bintray.com/sbt/debian/sbt-1.3.4.deb && \
  dpkg -i sbt-1.3.4.deb && \
  rm sbt-1.3.4.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion

WORKDIR /ChordSim

ADD . /ChordSim

CMD sbt clean compile
CMD sbt test
CMD sbt run