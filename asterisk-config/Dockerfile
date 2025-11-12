# Use Debian as the base image
FROM debian:bookworm-slim

# Set environment variables to avoid interactive prompts
ENV DEBIAN_FRONTEND=noninteractive

# Install all dependencies required for Asterisk 22
RUN apt-get update && \
    apt-get install -y \
        wget \
        build-essential \
        libjansson-dev \
        libxml2-dev \
        uuid-dev \
        pkg-config \
        libncurses5-dev \
        libssl-dev \
        libedit-dev \
        libsqlite3-dev \
        curl \
        net-tools \
        vim \
        git \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Download and extract Asterisk 22
RUN wget http://downloads.asterisk.org/pub/telephony/asterisk/asterisk-22-current.tar.gz && \
    tar -xzf asterisk-22-current.tar.gz && \
    cd asterisk-22*/ && \
    ./configure && \
    make && \
    make install && \
    make samples && \
    make config

# Expose SIP ports
EXPOSE 5060/udp
EXPOSE 5060/tcp

# Create the asterisk user and fix ownership
RUN adduser --disabled-password --gecos "" asterisk && \
    chown -R asterisk:asterisk /var/lib/asterisk /etc/asterisk /var/log/asterisk || true


# Set Asterisk as the entrypoint
ENTRYPOINT ["/usr/sbin/asterisk", "-f", "-U", "asterisk", "-vvv"]
