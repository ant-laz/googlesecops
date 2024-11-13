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

import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.tonyzaro.model.SolacePayload;
import com.tonyzaro.model.SolacePayloadOrBuilder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.Compression;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.solace.SolaceIO;
import org.apache.beam.sdk.io.solace.broker.BasicAuthJcsmpSessionServiceFactory;
import org.apache.beam.sdk.io.solace.broker.BasicAuthSempClientFactory;
import org.apache.beam.sdk.io.solace.data.Solace;
import org.apache.beam.sdk.io.solace.data.Solace.Queue;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.PCollection;
import org.joda.time.Duration;
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

    @Description("Cloud Storage URI")
    @Default.String("gs://bucket/directory")
    String getStoragePath();
    void setStoragePath(String value);
  }

  // ---------   DoFn ------------------------------------------------------------------------------
  static class ProcessSolace extends DoFn<Solace.Record, SolacePayload> {

    @ProcessElement
    public void processElement(@Element Solace.Record msg, OutputReceiver<SolacePayload> out)
        throws InvalidProtocolBufferException {
      ByteBuffer solacePayload =  msg.getPayload();
      String solacePayloadDecoded = StandardCharsets.UTF_8.decode(solacePayload).toString();
      SolacePayload.Builder payloadBuilder = SolacePayload.newBuilder();
      JsonFormat.parser().merge(solacePayloadDecoded, payloadBuilder);
      SolacePayload payload = payloadBuilder.build();
      out.output(payload);

      //debug
      LOG.info(payload.toString());
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

    // Assign solace messages into fixed windows
    PCollection<Solace.Record> windowed = events.apply(Window.<Solace.Record>into(FixedWindows.of(
        Duration.standardSeconds(60))));

    // Decode Solace payload into JSON string & then de-serialize into Protobuf message
    PCollection<SolacePayload> moveReviews = windowed.apply(ParDo.of(new ProcessSolace()));

    // TODO:: Transform SolacePayload into format for Chronicle API
    // PCollection<ChronicleLogData> logdata = movieReviews.apply()

    // TODO:: Transform into log import log for chronicle API
    // PCollection<LogsImportLog> logmsg = logdata.apply()

    // TODO:: Group together LogsImportLog to create send multiple logs per Chronicle API request
    // PCollection<GroupedLogs> logsgrouped= logs.apply(ParDo.GroupIntoBatches)

    // TODO:: Take many LogsImportLog and create 1 LogsImportRequest
    // PCollection<LogsImportSource> logs = events.apply(ParDo.of(new ConvertSolaceToLog)
    // PCollection<LogsImportRequest> requests = logsgrouped.apply(ParDo.of(new FormAPIRequests)

    // TODO:: Make the Request to the Chronicle API
    //PCollection<Success_Tag, Failure_Tag> result = requests.apply(ParDo.MakeAPIRequests)

    // TODO:: Remove output to Google Cloud Storage
    // Write JSON strings to Google Cloud Storage
    // moveReviews.apply(
    //     TextIO
    //         .write()
    //         .withWindowedWrites()
    //         .to(myOptions.getStoragePath())
    //         .withSuffix(".json")
    //         .withCompression(Compression.GZIP)
    // );

    //execute the pipeline
    pipeline.run().waitUntilFinish();
  }
}