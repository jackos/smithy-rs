use kms::operation::GenerateRandom;
use kms::Region;
use tracing_subscriber::fmt::SubscriberBuilder;
use tracing_subscriber::fmt::format::FmtSpan;
use aws_hyper::StandardClient;

#[tokio::main]
async fn main() {
    SubscriberBuilder::default().with_env_filter("info").with_span_events(FmtSpan::CLOSE).init();
    let config = kms::Config::builder()
        // region can also be loaded from AWS_DEFAULT_REGION, just remove this line.
        .region(Region::new("us-east-1"))
        // creds loaded from environment variables, or they can be hard coded.
        // Other credential providers not currently supported
        .build();
    let client: StandardClient = aws_hyper::Client::https();
    let data = client
        .call(GenerateRandom::builder().number_of_bytes(64).build(&config))
        .await
        .expect("failed to generate random data");
    println!("{:?}", data);
    assert_eq!(data.plaintext.expect("should have data").as_ref().len(), 64);
}