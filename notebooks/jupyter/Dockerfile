FROM quay.io/jupyter/minimal-notebook:latest

RUN mkdir /home/jovyan/resources

USER root
WORKDIR /home/jovyan

# Install dependencies: Java 21 and Maven
RUN apt-get update && apt-get install -y openjdk-21-jdk maven

# Copy the pre-created Maven project and jjava-glue project
COPY ./notebooks/jupyter/java /home/jovyan/java
COPY ./notebooks/jupyter/install.py /home/jovyan/install.py

# Use Maven to download dependencies for JJava
WORKDIR /home/jovyan/java

# Download the JJava jar directly
RUN mvn dependency:get -Dartifact=org.dflib.jjava:jjava:1.0-M3 -Ddest=./ -Dtransitive=false
RUN mv jjava-1.0-M3.jar jjava.jar

# Copy RedisVL source and build it
COPY ./core /home/jovyan/redisvl-src/core
COPY ./gradle /home/jovyan/redisvl-src/gradle
COPY ./gradlew /home/jovyan/redisvl-src/
COPY ./gradlew.bat /home/jovyan/redisvl-src/
COPY ./build.gradle.kts /home/jovyan/redisvl-src/
COPY ./settings.gradle.kts /home/jovyan/redisvl-src/
COPY ./spotbugs-exclude.xml /home/jovyan/redisvl-src/

# Build RedisVL from source
WORKDIR /home/jovyan/redisvl-src
RUN chmod +x gradlew && ./gradlew clean build publishToMavenLocal -x test

# Back to java directory for dependencies
WORKDIR /home/jovyan/java

# Copy the built JAR to a known location (excluding javadoc and sources JARs)
RUN cp /home/jovyan/redisvl-src/core/build/libs/redisvl-0.0.1.jar /home/jovyan/java/redisvl-core.jar

# Download all dependencies including Jedis and its transitive dependencies
RUN mvn dependency:copy-dependencies -DoutputDirectory=./lib

# Create a list of dependencies for the classpath
RUN find ./lib -name "*.jar" | tr '\n' ':' > classpath.txt
# Add the jjava.jar to the classpath
RUN echo -n "/home/jovyan/java/jjava.jar:" >> classpath.txt
# Add RedisVL JAR to classpath
RUN echo -n "/home/jovyan/java/redisvl-core.jar" >> classpath.txt

# Install the kernel with classpath configuration
WORKDIR /home/jovyan
RUN python install.py --prefix /opt/conda/ --classpath $(cat /home/jovyan/java/classpath.txt)

# Clean up Maven artifacts but keep the jjava.jar, redisvl-core.jar and lib directory
RUN rm -rf /home/jovyan/java/target /home/jovyan/java/.m2 /home/jovyan/java/pom.xml \
    /home/jovyan/java/classpath.txt \
    /home/jovyan/redisvl-src \
    && rm -f /home/jovyan/install.py

# Install conda packages from environment.yml
COPY ./notebooks/jupyter/environment.yml /tmp/
RUN conda env update -f /tmp/environment.yml && \
    conda clean --all -f -y && \
    fix-permissions "${CONDA_DIR}" && \
    fix-permissions "/home/${NB_USER}"

WORKDIR /home/jovyan
USER $NB_UID

# Override the default command to disable authentication
CMD ["jupyter", "lab", "--ip=0.0.0.0", "--port=8888", "--no-browser", "--ServerApp.token=", "--ServerApp.password=", "--ServerApp.allow_root=True"]