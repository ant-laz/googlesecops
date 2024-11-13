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
import com.google.protobuf.Timestamp;

import com.tonyzaro.model.ChronicleLogData;
import com.tonyzaro.model.LogsImportLog;
import com.tonyzaro.model.LogsImportRequest;
import com.tonyzaro.model.LogsImportSource;
import com.tonyzaro.model.SolacePayload;
import com.tonyzaro.model.SolacePayloadOrBuilder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import java.util.Calendar;
import java.util.Random;
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
import org.apache.beam.sdk.transforms.GroupIntoBatches;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
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
      ByteBuffer solacePayload = msg.getPayload();
      String solacePayloadDecoded = StandardCharsets.UTF_8.decode(solacePayload).toString();
      SolacePayload.Builder payloadBuilder = SolacePayload.newBuilder();
      JsonFormat.parser().merge(solacePayloadDecoded, payloadBuilder);
      SolacePayload payload = payloadBuilder.build();
      out.output(payload);

      //debug
      //LOG.info(payload.toString());
    }
  }

  // ---------   DoFn ------------------------------------------------------------------------------
    static class MapSolaceToChronicle extends DoFn<SolacePayload, ChronicleLogData> {

    @ProcessElement
    public void processElement(@Element SolacePayload msg, OutputReceiver<ChronicleLogData> out) {

      ChronicleLogData log = ChronicleLogData
          .newBuilder()
          .setMessage(msg.getMessage())
          .setReview(msg.getReview())
          .setUrl(msg.getUrl())
          .setTimestampIsoFormat(msg.getCriticalFields().getTimestampIsoFormat())
          .setUuaa(msg.getMetadata().getUuaa())
          .setDatachannel(msg.getMetadata().getDatachannel())
          .setWriteBq(msg.getMetadata().getWriteBq())
          .setWriteGcs(msg.getMetadata().getWriteGcs())
          .build();
      out.output(log);

      //debug
      //LOG.info(log.toString());
    }
  }

  // ---------   DoFn ------------------------------------------------------------------------------
  static class FormatLogDataForImport extends DoFn<ChronicleLogData, LogsImportLog> {

    @ProcessElement
    public void processElement(@Element ChronicleLogData msg, OutputReceiver<LogsImportLog> out) {
      // For demo purposes, we simply fix the events to all have the same timestamp
      // https://protobuf.dev/reference/protobuf/google.protobuf/#timestamp
      // timestamp1 used for log_entry_time, the timestamp of the log entry
      // TODO :: Make demo more realistic timestamp1 = current time & timestamp2 = timestamp1+1sec
      Calendar c1 = Calendar.getInstance();
      c1.set(2024, 11, 1, 23, 30, 30);
      com.google.protobuf.Timestamp timestamp1 = com.google.protobuf.Timestamp
          .newBuilder()
          .setSeconds(c1.getTimeInMillis() / 1000)
          .setNanos((int) ((c1.getTimeInMillis() % 1000) * 1000000))
          .build();
      // timestamp2 used for collection_time, time after the above when log was collected
      Calendar c2 = Calendar.getInstance();
      c2.set(2024, 11, 1, 23, 31, 30);
      com.google.protobuf.Timestamp timestamp2 = com.google.protobuf.Timestamp
          .newBuilder()
          .setSeconds(c2.getTimeInMillis() / 1000)
          .setNanos((int) ((c2.getTimeInMillis() % 1000) * 1000000))
          .build();

      LogsImportLog log = LogsImportLog
          .newBuilder()
          .setData(msg.toByteString())
          .setLogEntryTime(timestamp1)
          .setCollectionTime(timestamp2)
          .build();

      out.output(log);

      //debug
      //LOG.info(log.toString());
    }
  }

  // ---------   DoFn ------------------------------------------------------------------------------
  static class AllocateBatchID extends DoFn<LogsImportLog, KV<Integer,LogsImportLog>> {

    @ProcessElement
    public void processElement(@Element LogsImportLog msg, OutputReceiver<KV<Integer,LogsImportLog>> out) {
      Random r = new Random();
      KV<Integer, LogsImportLog> logWithBatchId = KV.of(r.nextInt(100), msg);
      out.output(logWithBatchId);

      //debug
      // LOG.info(logWithBatchId.toString());
    }
  }

  // ---------   DoFn ------------------------------------------------------------------------------
  static class FormChronicleRequests extends DoFn<KV<Integer, Iterable<LogsImportLog>>, LogsImportRequest> {

    @ProcessElement
    public void processElement(@Element KV<Integer, Iterable<LogsImportLog>> msg, OutputReceiver<LogsImportRequest> out) {
      LogsImportRequest request  = LogsImportRequest
          .newBuilder()
          .setInlineSource(
              LogsImportSource
                  .newBuilder()
                  .addAllLogs(msg.getValue())
                  .setForwarder("asdf")
                  .build()
          )
          .build();
      
      out.output(request);

      //debug
      //LOG.info(request.toString());
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
    PCollection<SolacePayload> solacePayload = windowed.apply(ParDo.of(new ProcessSolace()));

    // Transform SolacePayload into format for Chronicle API
    PCollection<ChronicleLogData> chronicleLogData = solacePayload.apply(ParDo.of(new MapSolaceToChronicle()));

    // Transform into log import log for chronicle API. Cloud be combined with above
    PCollection<LogsImportLog> chronicleLog = chronicleLogData.apply(ParDo.of(new FormatLogDataForImport()));

    // Assign random batch ids to messages, for later grouping
    PCollection<KV<Integer, LogsImportLog>> logWithBatchID = chronicleLog.apply(ParDo.of(new AllocateBatchID()));

    // Group together logs (using batch ids) to create send multiple logs per API request
    PCollection<KV<Integer, Iterable<LogsImportLog>>> logsGrouped = logWithBatchID
        .apply(
            GroupIntoBatches.<Integer,LogsImportLog>ofSize(100)
            .withMaxBufferingDuration(Duration.millis(1000)));

    // Take many LogsImportLog and create 1 LogsImportRequest
    PCollection<LogsImportRequest> chronicleRequests = logsGrouped.apply(ParDo.of(new FormChronicleRequests()));

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