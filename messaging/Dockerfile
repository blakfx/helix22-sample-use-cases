# This is an example Dockerfile for Centos 7
FROM centos:7.8.2003

# Move the files that shouldn't be mounted as volumes
# (ie, stay the same after "reboots")
# Note: If you want to load a different native_dep configuration
#       to the container, do it here!
COPY native_dep/CentOS-7_x86_64_shared_Release /native_dep/

# Expose ports for the server
EXPOSE 8080 7777 8000 22

# Install Helix dependencies (on CentOS)
RUN yum -y install libffi   \
	gmp                     \
	nettle                  \
	libtasn1                \
	p11-kit                 \
	bzip2-libs              \
	zlib                    \
	elfutils-libelf         \
	pcre                    \
	libattr                 \
	gnutls                  \
	elfutils-libs           \
	glibc                   \
	libgpg-error            \
	libgcrypt               \
	xz-libs                 \
	libselinux              \
	libcap                  \
	glibc                   \
	libgcc                  \
	libstdc++               \
	libmicrohttpd           \
	lz4                     \
	systemd-libs            \
	libuuid                 \
	java-11-openjdk-devel 	\
	unzip					\
	wget					\
	net-tools				\
	tmux					\
	openssh-server			\
	openssh-clients			\
	openssl
	
# Install Tomcat 9.0.38
RUN cd /tmp	\
	&& useradd -m -U -d /opt/tomcat -s /bin/false tomcat \
	&& wget https://downloads.apache.org/tomcat/tomcat-9/v9.0.38/bin/apache-tomcat-9.0.38.tar.gz \
	&& tar -xf apache-tomcat-9.0.38.tar.gz \
	&& mv apache-tomcat-9.0.38 /opt/tomcat/ \
	&& ln -s /opt/tomcat/apache-tomcat-9.0.38 /opt/tomcat/latest \
	&& chown -R tomcat: /opt/tomcat \
	&& sh -c 'chmod +x /opt/tomcat/latest/bin/*.sh'

# Install Maven 3.6.3
RUN wget https://www-us.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz -P /tmp && \
	tar xf /tmp/apache-maven-3.6.3-bin.tar.gz -C /opt && \
	ln -s /opt/apache-maven-3.6.3 /opt/maven && \
	echo "export JAVA_HOME=/usr/lib/jvm/jre-11-openjdk" > /etc/profile.d/maven.sh && \
	echo "export M2_HOME=/opt/maven" >> /etc/profile.d/maven.sh && \
	echo "export MAVEN_HOME=/opt/maven" >> /etc/profile.d/maven.sh && \
	echo "export PATH=/opt/maven/bin:${PATH}" >> /etc/profile.d/maven.sh && \
	source /etc/profile.d/maven.sh
