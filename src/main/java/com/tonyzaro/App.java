package com.tonyzaro;

//  Copyright 2024 Google LLC
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

import com.google.cloud.pubsublite.proto.PubSubMessage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.solace.data.Solace.Record;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.io.solace.SolaceIO;
import org.apache.beam.sdk.io.solace.broker.BasicAuthJcsmpSessionServiceFactory;
import org.apache.beam.sdk.io.solace.broker.BasicAuthSempClientFactory;
import org.apache.beam.sdk.io.solace.data.Solace;
import org.apache.beam.sdk.io.solace.data.Solace.Queue;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class App {

  // ---------   LOGGER ----------------------------------------------------------------------------
  // https://cloud.google.com/dataflow/docs/guides/logging
  // Instantiate Logger
  private static final Logger LOG = LoggerFactory.getLogger(App.class);

  // ---------   COMMAND LINE OPTIONS --------------------------------------------------------------
  // For custom command line options
  public interface MyAppOptions extends PipelineOptions {

    @Description("SEMP Hostname")
    @Default.String("http://localhost")
    String getSempHostname();
    void setSempHostname(String value);

    @Description("JCSMP Hostname")
    @Default.String("http://localhost")
    String getJcsmpHostname();
    void setJcsmpHostname(String value);

    @Description("Username")
    @Default.String("username")
    String getUsername();
    void setUsername(String value);

    @Description("Password")
    @Default.String("*****")
    String getPassword();
    void setPassword(String value);

    @Description("VPN Name")
    @Default.String("default")
    String getVpnName();
    void setVpnName(String value);

    @Description("Queue name")
    @Default.String("my-queue")
    String getQueueName();
    void setQueueName(String value);
  }

  // ---------   DoFn ------------------------------------------------------------------------------
  static class InspectSolace extends DoFn<Solace.Record, Solace.Record> {

    @ProcessElement
    public void processElement(@Element Solace.Record msg, OutputReceiver<Solace.Record> out){
      out.output(msg);
      LOG.info("Solace payload = " + StandardCharsets.UTF_8.decode(msg.getPayload()).toString());
    }
  }

  // ---------   Pipeline---------------------------------------------------------------------------
  public static void main(String[] args) {
    // Initialize the pipeline options
    PipelineOptionsFactory.register(MyAppOptions.class);
    MyAppOptions myOptions = PipelineOptionsFactory
        .fromArgs(args)
        .withValidation()
        .as(MyAppOptions.class);

    // create the main pipeline
    Pipeline pipeline = Pipeline.create(myOptions);


    // Read messages from Solace
    PCollection<Solace.Record> events = pipeline.apply("Read from Solace",
        SolaceIO.read().from(Queue.fromName(myOptions.getQueueName())).
            withSempClientFactory(
                BasicAuthSempClientFactory.builder()
                    .host(myOptions.getSempHostname())
                    .username(myOptions.getUsername())
                    .password(myOptions.getPassword())
                    .vpnName(myOptions.getVpnName())
                    .build())
            .withSessionServiceFactory(
                BasicAuthJcsmpSessionServiceFactory.builder()
                    .host(myOptions.getJcsmpHostname())
                    .username(myOptions.getUsername())
                    .password(myOptions.getPassword())
                    .vpnName(myOptions.getVpnName())
                    .build()));

    // Inspect Solace.Record objects
    PCollection<Solace.Record> messages = events.apply(ParDo.of(new InspectSolace()));

    //TODO select a JSON library in Java

    //TODO convert Solace.Record in to JSON

    //TODO convert JSON to string

    //TODO write JSON string to GCS

    //TODO setup feed in SecOps, confirm no data there

    //TODO kick off import job in SecOps

    //TODO confirm data lands in SecOps

    //execute the pipeline
    pipeline.run().waitUntilFinish();
  }
}