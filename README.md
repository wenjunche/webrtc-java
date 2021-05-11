# webrtc-java
WebRTC support for OpenFin Java adapter

## Overview
This repo is created to run performance tests of WebRTC in OpenFin Runtime.  It should be tested with [this repo](git@github.com:wenjunche/webrtc-performance.git) for peer-peer connection with javascript.

## Run the test
WebRTCPerf is the main class for running the test.  Currently it only supports sending messages from Java side to javascript side.  In order to establish connection, both sides have to use the same Pairing Code.


