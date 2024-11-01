package com.tonyzaro;

import org.apache.beam.sdk.schemas.JavaFieldSchema;
import org.apache.beam.sdk.schemas.annotations.DefaultSchema;

// As per https://beam.apache.org/documentation/programming-guide/#element-type
// To have a PCollection<MovieReview> need to tell Beam how to encode MoveReview into a byte string.
// Why? To support distributed processing, each element of PCollection<MovieReview> is converted
// to a byte string, so it can be passed around to distributed workers.
// The Beam SDKs provide a data encoding mechanism.
// For example, by using annotation "@DefaultSchema(JavaFieldSchema.class)"
// Beam will automatically infer the correct schema for this PCollection.
// No coder is needed as a result to tell beam how to encode MovieReview as a byte string
@DefaultSchema(JavaFieldSchema.class)
public class MovieReview {

  public String getReview() {
    return review;
  }

  public void setReview(String review) {
    this.review = review;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  private String review;
  private String url;
}
